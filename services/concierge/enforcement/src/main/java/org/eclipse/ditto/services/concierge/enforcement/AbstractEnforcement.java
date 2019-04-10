/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import akka.actor.ActorRef;
import akka.event.DiagnosticLoggingAdapter;
import akka.pattern.AskTimeoutException;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Implementations only need to implement {@link #enforce()} in which they
 * check if the passed in {@code signal} is authorized and forward it accordingly or respond with an error to the passed
 * in {@code sender}.
 */
public abstract class AbstractEnforcement<T extends Signal> {

    private final Contextual<T> context;

    protected AbstractEnforcement(final Contextual<T> context) {
        this.context = context;
    }

    /**
     * @return the Executor to use in order to perform asynchronous operations in enforcement.
     */
    protected Executor getEnforcementExecutor() {
        return context.getEnforcerExecutor();
    }

    /**
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     * CAUTION: May deliver a failed future.
     *
     * @return future after enforcement was performed.
     */
    public abstract CompletionStage<Void> enforce();

    /**
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     * The result future always succeeds.
     *
     * @return future after enforcement was performed.
     */
    public CompletionStage<Void> enforceSafely() {
        return enforce().whenComplete(handleEnforcementCompletion());
    }

    private BiConsumer<Void, Throwable> handleEnforcementCompletion() {
        return (_void, throwable) -> {
            if (throwable != null) {
                final Throwable error = throwable instanceof CompletionException
                        ? throwable.getCause()
                        : throwable;
                reportError("Error thrown during enforcement", error);
            }
        };
    }

    /**
     * Reply a message to sender.
     *
     * @param message message to forward.
     */
    protected void replyToSender(final Object message) {
        sender().tell(message, self());
    }

    /**
     * Report unexpected error or unknown response.
     */
    protected void reportUnexpectedErrorOrResponse(final String hint, final Object response,
            @Nullable final Throwable error) {

        if (error != null) {
            reportUnexpectedError(hint, error);
        } else {
            reportUnknownResponse(hint, response);
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}, it is send to the {@code sender}
     * without modification, otherwise it is wrapped inside a {@link GatewayInternalErrorException}.
     */
    protected void reportError(final String hint, final Throwable error) {
        if (error instanceof DittoRuntimeException) {
            log().info("{} - {}: {}", hint, error.getClass().getSimpleName(), error.getMessage());
            sender().tell(error, self());
        } else {
            reportUnexpectedError(hint, error);
        }
    }

    /**
     * Report unexpected error.
     */
    protected void reportUnexpectedError(final String hint, final Throwable error) {
        log().error(error, "Unexpected error {} - {}: {}", hint, error.getClass().getSimpleName(),
                error.getMessage());

        sender().tell(mapToExternalException(error), self());
    }

    /**
     * Report unknown response.
     */
    protected void reportUnknownResponse(final String hint, final Object response) {
        log().error("Unexpected response {}: <{}>", hint, response);

        sender().tell(GatewayInternalErrorException.newBuilder().dittoHeaders(dittoHeaders()).build(), self());
    }

    private DittoRuntimeException mapToExternalException(final Throwable error) {
        if (error instanceof GatewayInternalErrorException) {
            return (GatewayInternalErrorException) error;
        } else {
            log().error(error, "Unexpected non-DittoRuntimeException error - responding with " +
                    "GatewayInternalErrorException - {} :{}", error.getClass().getSimpleName(), error.getMessage());
            return GatewayInternalErrorException.newBuilder()
                    .cause(error)
                    .dittoHeaders(dittoHeaders())
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
        return context.getAskTimeout();
    }

    /**
     * @return the entity ID.
     */
    protected EntityId entityId() {
        return context.getEntityId();
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
        return context.getLog();
    }

    /**
     * @return the diagnostic logging adapter.
     */
    protected DiagnosticLoggingAdapter log() {
        LogUtil.enhanceLogWithCorrelationId(context.getLog(), dittoHeaders());
        return context.getLog();
    }

    /**
     * @return Akka pubsub mediator.
     */
    protected ActorRef pubSubMediator() {
        return context.getPubSubMediator();
    }

    /**
     * @return actor reference of the enforcer actor this object belongs to.
     */
    protected ActorRef self() {
        return context.getSelf();
    }

    /**
     * @return the sender of the sent {@link #signal()}
     */
    protected ActorRef sender() {
        return context.getSender();
    }

    /**
     * @return the sent Signal of subtype {@code <T>}
     */
    protected T signal() {
        return context.getMessage();
    }

    /**
     * @return the DittoHeaders of the sent {@link #signal()}
     */
    protected DittoHeaders dittoHeaders() {
        return signal().getDittoHeaders();
    }

    /**
     * @return the {@link org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor} reference
     */
    protected ActorRef conciergeForwarder() {
        return context.getConciergeForwarder();
    }

}
