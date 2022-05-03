/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.WithType;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.model.ThingConstants;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;

/**
 * Contains self-type requirements for aspects of enforcer actor dealing with specific commands.
 * Implementations only need to implement {@link #enforce()} in which they
 * check if the passed in {@code signal} is authorized and forward it accordingly or respond with an error to the passed
 * in {@code sender}.
 *
 * TODO TJ candidate for removal - all implementations should in the end extends from AbstractEnforcementReloaded instead
 */
public abstract class AbstractEnforcement<C extends Signal<?>> {

    /**
     * Context of the enforcement step: sender, self, signal and so forth.
     */
    protected final Contextual<C> context;

    /**
     * Create an enforcement step from its context.
     *
     * @param context the context of the enforcement step.
     */
    protected AbstractEnforcement(final Contextual<C> context) {
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
            if (null != result) {
                final ThreadSafeDittoLoggingAdapter l = result.getLog().withCorrelationId(result);
                final String typeHint = result.getMessageOptional()
                        .filter(WithType.class::isInstance)
                        .map(msg -> ((WithType) msg).getType())
                        .orElse("?");
                l.info("Completed enforcement of contextual message type <{}> with outcome 'success'",
                        typeHint);
                l.debug("Completed enforcement of contextual message type <{}> with outcome 'success' " +
                        "and headers: <{}>", typeHint, result.getDittoHeaders());
            } else {
                log().info("Completed enforcement of contextual message with outcome 'failed' and headers: " +
                        "<{}>", dittoHeaders());
            }
            return Objects.requireNonNullElseGet(result,
                    () -> withMessageToReceiver(reportError("Error thrown during enforcement", throwable), sender()));
        };
    }

    /**
     * Report unexpected error or unknown response.
     */
    protected DittoRuntimeException reportErrorOrResponse(final String hint, @Nullable final Object response,
            @Nullable final Throwable error) {

        if (error != null) {
            return reportError(hint, error);
        } else if (response instanceof Throwable throwable) {
            return reportError(hint, throwable);
        } else if (response != null) {
            return reportUnknownResponse(hint, response);
        } else {
            return reportError(hint, new NullPointerException("Response and error were null."));
        }
    }

    /**
     * Reports an error differently based on type of the error. If the error is of type
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException}, it is returned as is
     * (without modification), otherwise it is wrapped inside a
     * {@link org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException}.
     *
     * @param hint hint about the nature of the error.
     * @param throwable the error.
     * @return DittoRuntimeException suitable for transmission of the error.
     */
    protected DittoRuntimeException reportError(final String hint, @Nullable final Throwable throwable) {
        final Throwable error = throwable == null
                ? new NullPointerException("Result and error are both null")
                : throwable;
        final var dre = DittoRuntimeException.asDittoRuntimeException(
                error, cause -> reportUnexpectedError(hint, cause));
        log().info("{} - {}: {}", hint, dre.getClass().getSimpleName(), dre.getMessage());
        return dre;
    }


    /**
     * Report unexpected error.
     */
    private DittoRuntimeException reportUnexpectedError(final String hint, final Throwable error) {
        log().error(error, "Unexpected error {} - {}: {}", hint, error.getClass().getSimpleName(),
                error.getMessage());

        return DittoInternalErrorException.newBuilder()
                .cause(error)
                .dittoHeaders(dittoHeaders())
                .build();
    }

    /**
     * Report unknown response.
     */
    protected DittoInternalErrorException reportUnknownResponse(final String hint, final Object response) {
        log().error("Unexpected response {}: <{}>", hint, response);

        return DittoInternalErrorException.newBuilder().dittoHeaders(dittoHeaders()).build();
    }

    /**
     * Extend a signal by subject headers given with granted and revoked READ access.
     * The subjects are provided by the given enforcer for the resource type {@link ThingConstants#ENTITY_TYPE}.
     *
     * @param signal the signal to extend.
     * @param enforcer the enforcer.
     * @return the extended signal.
     */
    protected static <T extends Signal<T>> T addEffectedReadSubjectsToThingSignal(final Signal<T> signal,
            final Enforcer enforcer) {

        final var resourceKey = ResourceKey.newInstance(ThingConstants.ENTITY_TYPE, signal.getResourcePath());
        final var authorizationSubjects = enforcer.getSubjectsWithUnrestrictedPermission(resourceKey, Permission.READ);
        final var newHeaders = DittoHeaders.newBuilder(signal.getDittoHeaders())
                .readGrantedSubjects(authorizationSubjects)
                .build();

        return signal.setDittoHeaders(newHeaders);
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
     * @return the configuration of "ask with retry" pattern during enforcement.
     */
    protected AskWithRetryConfig getAskWithRetryConfig() {
        return context.getAskWithRetryConfig();
    }

    /**
     * @return the entity ID.
     */
    protected EnforcementCacheKey entityId() {
        return context.getCacheKey();
    }

    /**
     * @param withPotentialDittoHeaders the object which potentially contains DittoHeaders from which a
     * {@code correlation-id} can be extracted in order to enhance the returned DiagnosticLoggingAdapter
     * @return the diagnostic logging adapter.
     */
    protected ThreadSafeDittoLoggingAdapter log(final Object withPotentialDittoHeaders) {
        if (withPotentialDittoHeaders instanceof WithDittoHeaders withDittoHeaders) {
            return context.getLog().withCorrelationId(withDittoHeaders);
        }
        if (withPotentialDittoHeaders instanceof DittoHeaders dittoHeaders) {
            return context.getLog().withCorrelationId(dittoHeaders);
        }
        return context.getLog();
    }

    /**
     * @return the diagnostic logging adapter.
     */
    protected ThreadSafeDittoLoggingAdapter log() {
        return context.getLog().withCorrelationId(dittoHeaders());
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
    protected C signal() {
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
    protected <S extends WithDittoHeaders> Contextual<S> withMessageToReceiver(
            @Nullable final S message,
            @Nullable final ActorRef receiver) {

        return context.setMessage(message).withReceiver(receiver);
    }

    /**
     * Insert the passed {@code message} and {@code receiver} into the current {@link Contextual} {@link #context},
     * then insert {@code askFutureWithoutErrorHandling} after appending error handling logic to it.
     *
     * @param message the message to log when executing the contextual; usually the message to ask.
     * @param receiver the final receiver of the ask result.
     * @param askFutureWithoutErrorHandling supplier of a future that performs an ask-operation with command-order
     * guarantee, whose result is to be piped to {@code receiver}.
     * @param <T> type of results of the ask future.
     * @return a copy of the context with message, receiver and ask-future including error handling.
     */
    protected <T> Contextual<WithDittoHeaders> withMessageToReceiverViaAskFuture(final WithDittoHeaders message,
            final ActorRef receiver,
            final Supplier<CompletionStage<T>> askFutureWithoutErrorHandling) {

        return this.withMessageToReceiver(message, receiver)
                .withAskFuture(() -> askFutureWithoutErrorHandling.get()
                        .<Object>thenApply(x -> x)
                        .exceptionally(error -> this.reportError("Error thrown during enforcement", error))
                );
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
            final ActorRef receiver,
            final UnaryOperator<Object> wrapperFunction) {

        return context.setMessage(message).withReceiver(receiver).withReceiverWrapperFunction(wrapperFunction);
    }

    /**
     * @return the DittoHeaders of the sent {@link #signal()}
     */
    protected DittoHeaders dittoHeaders() {
        return signal().getDittoHeaders();
    }

    /**
     * @return the {@code ConciergeForwarderActor} reference
     */
    protected ActorRef commandForwarder() {
        return context.getCommandForwarder();
    }

}
