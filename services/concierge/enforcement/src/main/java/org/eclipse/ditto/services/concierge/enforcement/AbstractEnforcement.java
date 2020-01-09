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
import java.util.function.BiFunction;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.ResourceKey;
import org.eclipse.ditto.services.models.policies.Permission;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
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
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     * CAUTION: May deliver a failed future.
     *
     * @return future after enforcement was performed.
     */
    public abstract CompletionStage<Contextual<WithDittoHeaders>> enforce();

    /**
     * Performs authorization enforcement for the passed {@code signal}.
     * If the signal is authorized, the implementation chooses to which target to forward. If it is not authorized, the
     * passed {@code sender} will get an authorization error response.
     * The result future always succeeds.
     *
     * @return future after enforcement was performed.
     */
    public CompletionStage<Contextual<WithDittoHeaders>> enforceSafely() {
        return enforce().handle(handleEnforcementCompletion());
    }

    private BiFunction<Contextual<WithDittoHeaders>, Throwable, Contextual<WithDittoHeaders>> handleEnforcementCompletion() {
        return (result, throwable) -> {
            context.getStartedTimer()
                    .map(startedTimer -> startedTimer.tag("outcome", throwable != null ? "fail" : "success"))
                    .ifPresent(StartedTimer::stop);
            if (throwable != null) {
                final Throwable error = throwable instanceof CompletionException
                        ? throwable.getCause()
                        : throwable;
                return withMessageToReceiver(reportError("Error thrown during enforcement", error), sender());
            } else {
                return result;
            }
        };
    }

    /**
     * Report unexpected error or unknown response.
     */
    protected DittoRuntimeException reportUnexpectedErrorOrResponse(final String hint, final Object response,
            @Nullable final Throwable error) {

        if (error != null) {
            return reportUnexpectedError(hint, error);
        } else {
            return reportUnknownResponse(hint, response);
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}, it is send to the {@code sender}
     * without modification, otherwise it is wrapped inside a {@link GatewayInternalErrorException}.
     * @return
     */
    protected DittoRuntimeException reportError(final String hint, final Throwable error) {
        if (error instanceof DittoRuntimeException) {
            log().info("{} - {}: {}", hint, error.getClass().getSimpleName(), error.getMessage());
            return (DittoRuntimeException) error;
        } else {
            return reportUnexpectedError(hint, error);
        }
    }

    /**
     * Report unexpected error.
     */
    protected DittoRuntimeException reportUnexpectedError(final String hint, final Throwable error) {
        log().error(error, "Unexpected error {} - {}: {}", hint, error.getClass().getSimpleName(),
                error.getMessage());

        return mapToExternalException(error);
    }

    /**
     * Report unknown response.
     */
    protected GatewayInternalErrorException reportUnknownResponse(final String hint, final Object response) {
        log().error("Unexpected response {}: <{}>", hint, response);

        return GatewayInternalErrorException.newBuilder().dittoHeaders(dittoHeaders()).build();
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
    protected static boolean isAskTimeoutException(final Object response, @Nullable final Throwable error) {
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
    protected EntityIdWithResourceType entityId() {
        return context.getEntityIdWithResourceType();
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
        if (withPotentialDittoHeaders instanceof DittoHeaders) {
            LogUtil.enhanceLogWithCorrelationId(context.getLog(), (DittoHeaders) withPotentialDittoHeaders);
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
     * Inserts the passed {@code message} and {@code receiver} into the current {@link Contextual} {@link #context}.
     *
     * @param message the message to insert into the current context.
     * @param receiver the ActorRef of the receiver which should get the message.
     * @param <S> the message's type
     * @return the adjusted context.
     */
    protected <S extends WithDittoHeaders> Contextual<S> withMessageToReceiver(final S message,
            final ActorRef receiver) {
        return context.withMessage(message).withReceiver(receiver);
    }

    /**
     * Inserts the passed {@code message} and {@code receiver} into the current {@link Contextual} {@link #context}
     * providing a function which shall be invoked prior to sending the {@code message} to the {@code receiver}.
     *
     * @param message the message to insert into the current context.
     * @param receiver the ActorRef of the receiver which should get the message.
     * @param wrapperFunction the function to apply prior to sending to the {@code receiver}.
     * @param <S> the message's type
     * @return the adjusted context.
     */
    protected <S extends WithDittoHeaders> Contextual<S> withMessageToReceiver(final S message,
            final ActorRef receiver, final Function<Object, Object> wrapperFunction) {
        return context.withMessage(message).withReceiver(receiver).withReceiverWrapperFunction(wrapperFunction);
    }

    /**
     * Sets the {@code null} receiver to the {@link #context} meaning that no message at all is emitted. Therefore
     * the {@code message} may also stay {@code null}.
     *
     * @return the adjusted context.
     */
    protected <S extends WithDittoHeaders> Contextual<S> withoutReceiver(final S message) {
        return context.withMessage(message).withReceiver(null);
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

    /**
     * Handle the passed {@code throwable} by sending it to the {@link #context}'s sender.
     *
     * @param throwable the occurred throwable (most likely a {@link DittoRuntimeException}) to send to the sender.
     * @return the built contextual including the DittoRuntimeException.
     */
    protected Contextual<WithDittoHeaders> handleExceptionally(final Throwable throwable) {
        final Contextual<T> newContext = context.withReceiver(context.getSender());

        final DittoRuntimeException dittoRuntimeException =
                DittoRuntimeException.asDittoRuntimeException(throwable,
                        cause -> GatewayInternalErrorException.newBuilder()
                                .cause(cause)
                                .build());

        return newContext.withMessage(dittoRuntimeException);
    }
}
