/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.akka.controlflow.Consume;
import org.eclipse.ditto.services.utils.akka.controlflow.WithSender;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.AskTimeoutException;
import akka.stream.Graph;
import akka.stream.SinkShape;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Implementations only need to implement {@link #enforce(Signal, ActorRef, DiagnosticLoggingAdapter)} in which they
 * check if the passed in {@link Signal} is authorized and forward it accordingly or respond with an error to the passed
 * in {@code sender}.
 * <p>
 * Do NOT call the methods outside this package.
 * </p>
 */
public abstract class AbstractEnforcement<T extends Signal> {

    // TODO: make context a part of the argument.
    private final Context context;

    protected AbstractEnforcement(final Context context) {
        this.context = context;
    }

    /**
     * @return the Executor to use in order to perform asynchronous operations in enforcement.
     */
    protected Executor getEnforcementExecutor() {
        return context.enforcerExecutor;
    }

    /**
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     *
     * @param signal the signal to authorize.
     * @param sender sender of the signal.
     * @param log the logger to use for logging.
     * @return future after enforcement was performed.
     */
    public abstract CompletionStage<Void> enforce(T signal, ActorRef sender, DiagnosticLoggingAdapter log);

    /**
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     *
     * @param signal the signal to authorize.
     * @param sender sender of the signal.
     * @param log the logger to use for logging.
     * @return future after enforcement was performed.
     */
    public CompletionStage<Void> enforceSafely(final T signal, final ActorRef sender,
            final DiagnosticLoggingAdapter log) {

        return enforce(signal, sender, log).whenComplete(handleEnforcementCompletion(signal, sender));
    }

    Graph<SinkShape<WithSender<T>>, NotUsed> toGraph() {
        return Consume.of((signal, sender) -> enforceSafely(signal, sender, context.log));
    }

    private BiConsumer<Void, Throwable> handleEnforcementCompletion(final T signal, final ActorRef sender) {
        return (_void, throwable) -> {
            if (throwable != null) {
                final Throwable error = throwable instanceof CompletionException
                        ? throwable.getCause()
                        : throwable;
                reportError("Error thrown during enforcement", sender, error, signal.getDittoHeaders());
            }
        };
    }

    /**
     * Reply a message to sender.
     *
     * @param message message to forward.
     * @param sender whom to reply to.
     * @return true.
     */
    protected boolean replyToSender(final Object message, final ActorRef sender) {
        sender.tell(message, self());
        return true;
    }

    /**
     * Report unexpected error or unknown response.
     */
    protected void reportUnexpectedErrorOrResponse(final String hint,
            final ActorRef sender,
            final Object response,
            final Throwable error,
            final DittoHeaders dittoHeaders) {

        if (error != null) {
            reportUnexpectedError(hint, sender, error, dittoHeaders);
        } else {
            reportUnknownResponse(hint, sender, response, dittoHeaders);
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}, it is send to the {@code sender}
     * without modification, otherwise it is wrapped inside a {@link GatewayInternalErrorException}.
     */
    protected void reportError(final String hint, final ActorRef sender, final Throwable error,
            final DittoHeaders dittoHeaders) {
        if (error instanceof DittoRuntimeException) {
            log(dittoHeaders).info("{}: {}", hint, error);
            sender.tell(error, self());
        } else {
            reportUnexpectedError(hint, sender, error, dittoHeaders);
        }
    }

    /**
     * Report unexpected error.
     */
    protected void reportUnexpectedError(final String hint, final ActorRef sender, final Throwable error,
            final DittoHeaders dittoHeaders) {
        log(dittoHeaders).error(error, "Unexpected error {}", hint);

        sender.tell(mapToExternalException(error, dittoHeaders), self());
    }

    /**
     * Report unknown response.
     */
    protected void reportUnknownResponse(final String hint, final ActorRef sender, final Object response,
            final DittoHeaders dittoHeaders) {
        log(dittoHeaders).error("Unexpected response {}: <{}>", hint, response);

        sender.tell(GatewayInternalErrorException.newBuilder().dittoHeaders(dittoHeaders).build(), self());
    }

    private DittoRuntimeException mapToExternalException(final Throwable error,
            final DittoHeaders dittoHeaders) {
        if (error instanceof GatewayInternalErrorException) {
            return (GatewayInternalErrorException) error;
        } else {
            log(dittoHeaders).error(error, "Unexpected non-DittoRuntimeException error - responding with " +
                    "GatewayInternalErrorException: {} {}", error.getClass().getSimpleName(), error.getMessage());
            return GatewayInternalErrorException.newBuilder()
                    .cause(error)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Extend a signal by read-subjects header given by an enforcer for the resource type {@code things}.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return the extended signal.
     */
    protected static <T extends Signal> T addReadSubjectsToThingSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        return addReadSubjectsToSignal(signal, getThingsReadSubjects(signal, enforcer));
    }

    /**
     * Extend a signal by read-subjects header given explicitly.
     *
     * @param <T> type of the signal.
     * @param signal the signal to extend.
     * @param readSubjects explicitly-given read subjects.
     * @return the extended signal.
     */
    protected static <T extends Signal> T addReadSubjectsToSignal(final Signal<T> signal,
            final Set<String> readSubjects) {

        final DittoHeaders newHeaders = signal.getDittoHeaders()
                .toBuilder()
                .readSubjects(readSubjects)
                .build();

        return signal.setDittoHeaders(newHeaders);
    }

    /**
     * Get read subjects from an enforcer for the resource type {@code things}.
     *
     * @param signal the signal to get read subjects for.
     * @param enforcer the enforcer.
     * @return read subjects of the signal.
     */
    protected static Set<String> getThingsReadSubjects(final Signal<?> signal, final Enforcer enforcer) {
        final ResourceKey resourceKey =
                ResourceKey.newInstance(ThingCommand.RESOURCE_TYPE, signal.getResourcePath());
        return enforcer.getSubjectIdsWithPermission(resourceKey, Permission.READ).getGranted();
    }

    /**
     * Check whether response or error from a future is {@code AskTimeoutException}.
     *
     * @param response response from a future.
     * @param error error thrown in a future.
     * @return whether either is {@code AskTimeoutException}.
     */
    protected static boolean isAskTimeoutException(final Object response, final Throwable error) {
        return error instanceof AskTimeoutException || response instanceof AskTimeoutException;
    }

    /**
     * @return Timeout duration for asking entity shard regions.
     */
    protected Duration getAskTimeout() {
        return context.askTimeout;
    }

    /**
     * @return the entity ID.
     */
    protected EntityId entityId() {
        return context.entityId;
    }

    /**
     * @param withPotentialDittoHeaders the object which potentially contains DittoHeaders from which a
     * {@code correlation-id} can be extracted in order to enhance the returned DiagnosticLoggingAdapter
     * @return the diagnostic logging adapter.
     */
    protected DiagnosticLoggingAdapter log(final Object withPotentialDittoHeaders) {
        if (withPotentialDittoHeaders instanceof WithDittoHeaders) {
            return log(((WithDittoHeaders<?>) withPotentialDittoHeaders).getDittoHeaders());
        }
        return context.log;
    }

    /**
     * @param dittoHeaders the DittoHeaders from which a {@code correlation-id} can be extracted in order to enhance
     * the returned DiagnosticLoggingAdapter.
     * @return the diagnostic logging adapter.
     */
    protected DiagnosticLoggingAdapter log(final DittoHeaders dittoHeaders) {
        if (context.log != null) {
            LogUtil.enhanceLogWithCorrelationId(context.log, dittoHeaders);
        }
        return context.log;
    }

    /**
     * @return Akka pubsub mediator.
     */
    protected ActorRef pubSubMediator() {
        return context.pubSubMediator;
    }

    /**
     * @return actor reference of the enforcer actor this object belongs to.
     */
    protected ActorRef self() {
        return context.self;
    }

    protected ActorRef conciergeForwarder() { return context.conciergeForwarder;}

    /**
     * Holds context information required by implementations of {@link AbstractEnforcement}.
     */
    public static final class Context {

        private final ActorRef pubSubMediator;
        private final Duration askTimeout;

        @Nullable
        private final EntityId entityId;

        @Nullable
        private final DiagnosticLoggingAdapter log;

        @Nullable
        private final ActorRef self;

        private final ActorRef conciergeForwarder;

        private final Executor enforcerExecutor;

        Context(
                final ActorRef pubSubMediator,
                final Duration askTimeout,
                final ActorRef conciergeForwarder, final Executor enforcerExecutor) {

            this(pubSubMediator, askTimeout, conciergeForwarder, enforcerExecutor, null, null, null);
        }

        Context(
                final ActorRef pubSubMediator,
                final Duration askTimeout,
                @Nullable final ActorRef conciergeForwarder,
                final Executor enforcerExecutor,
                @Nullable final EntityId entityId,
                @Nullable final DiagnosticLoggingAdapter log,
                @Nullable final ActorRef self) {
            this.pubSubMediator = pubSubMediator;
            this.askTimeout = askTimeout;
            this.conciergeForwarder = conciergeForwarder;
            this.enforcerExecutor = enforcerExecutor;
            this.entityId = entityId;
            this.log = log;
            this.self = self;
        }

        static <T> Context of(final Contextual<T> c) {
            return new Context(c.getPubSubMediator(), c.getAskTimeout(), c.getConciergeForwarder(),
                    c.getEnforcerExecutor(), c.getEntityId().orElse(null), c.getLog(), c.getSelf());
        }

        /**
         * Creates a new {@link Context} from this instance with the given parameters.
         *
         * @param actorContext the actor context.
         * @param log the logger.
         * @param enforcerExecutor the Executor to use in order to perform asynchronous operations in enforcement.
         * @return the created instance.
         */
        public Context with(final AbstractActor.ActorContext actorContext, final DiagnosticLoggingAdapter log,
                final Executor enforcerExecutor) {
            final ActorRef contextSelf = actorContext.self();
            return new Context(pubSubMediator, askTimeout, conciergeForwarder, enforcerExecutor,
                    decodeEntityId(contextSelf), log, contextSelf);
        }

        static EntityId decodeEntityId(final ActorRef self) {
            final String name = self.path().name();
            try {
                final String typeWithPath = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
                return EntityId.readFrom(typeWithPath);
            } catch (final UnsupportedEncodingException e) {
                throw new IllegalStateException("Unsupported encoding", e);
            }
        }
    }
}
