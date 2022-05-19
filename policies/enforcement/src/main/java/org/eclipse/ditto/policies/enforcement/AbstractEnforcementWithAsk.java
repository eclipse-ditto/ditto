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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.base.model.exceptions.AskException;
import org.eclipse.ditto.base.model.exceptions.DittoInternalErrorException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;
import org.eclipse.ditto.internal.utils.cacheloaders.AskWithRetry;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;

import akka.actor.ActorRef;
import akka.actor.Scheduler;
import akka.pattern.AskTimeoutException;

/**
 * Adds a common handling to ask an actor for a response and automatically filter the responses JSON view by an
 * enforcer.
 * TODO CR-11297 candidate for removal
 *
 * @param <C> The command type.
 * @param <R> The response type.
 */
public abstract class AbstractEnforcementWithAsk<C extends Signal<?>, R extends CommandResponse<?>>
        extends AbstractEnforcement<C> {

    private final Class<R> responseClass;

    /**
     * Create an enforcement step from its context.
     *
     * @param context the context of the enforcement step.
     */
    @SuppressWarnings("unchecked")
    protected AbstractEnforcementWithAsk(final Contextual<C> context, final Class<?> responseClass) {
        super(context);
        this.responseClass = (Class<R>) responseClass;
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code commandWithReadSubjects}.
     * The response is then be filtered by using the {@code enforcer}.
     *
     * @param actorToAsk the actor that should be asked.
     * @param commandWithReadSubjects the command that is used to ask.
     * @param enforcer the enforced used to filter the JSON view.
     * @param scheduler the scheduler to use for performing the "ask with retry".
     * @param executor the executor to use for performing the "ask with retry".
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    protected CompletionStage<R> askAndBuildJsonView(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final Enforcer enforcer,
            final Scheduler scheduler,
            final Executor executor) {

        return ask(actorToAsk, commandWithReadSubjects, "before building JsonView", scheduler, executor)
                .thenApply(response -> {
                    if (null != response) {
                        return filterJsonView(response, enforcer);
                    } else {
                        log(commandWithReadSubjects).error("Response before building JsonView was null at a place " +
                                "where it must never be null");
                        throw DittoInternalErrorException.newBuilder()
                                .dittoHeaders(commandWithReadSubjects.getDittoHeaders())
                                .build();
                    }
                });
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code commandWithReadSubjects}.
     * This method uses {@link AskWithRetry}.
     *
     * @param actorToAsk the actor that should be asked.
     * @param commandWithReadSubjects the command that is used to ask.
     * @param hint used for logging purposes.
     * @param scheduler the scheduler to use for performing the "ask with retry".
     * @param executor the executor to use for performing the "ask with retry".
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    protected CompletionStage<R> ask(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final String hint,
            final Scheduler scheduler,
            final Executor executor) {

        final BiFunction<ActorRef, Object, CompletionStage<R>> askWithRetry =
                (toAsk, message) -> AskWithRetry.askWithRetry(toAsk, message, getAskWithRetryConfig(), scheduler,
                        executor, getResponseCaster(commandWithReadSubjects, hint)
        );

        return ask(actorToAsk, commandWithReadSubjects, askWithRetry);
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code commandWithReadSubjects}.
     *
     * @param actorToAsk the actor that should be asked.
     * @param commandWithReadSubjects the command that is used to ask.
     * @param askStrategy a function which does the actual ask, e.g. with timeout or with retry.
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    protected CompletionStage<R> ask(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final BiFunction<ActorRef, Object, CompletionStage<R>> askStrategy) {

        return askStrategy.apply(actorToAsk, wrapBeforeAsk(commandWithReadSubjects))
                .exceptionally(throwable -> {
            final DittoRuntimeException dre = DittoRuntimeException.asDittoRuntimeException(throwable, cause ->
                    AskException.newBuilder()
                            .dittoHeaders(commandWithReadSubjects.getDittoHeaders())
                            .cause(cause)
                            .build());
            if (dre instanceof AskException) {
                final Optional<DittoRuntimeException> dittoRuntimeException =
                        handleAskTimeoutForCommand(commandWithReadSubjects, throwable);
                if (dittoRuntimeException.isPresent()) {
                    throw dittoRuntimeException.get();
                } else {
                    return null;
                }
            } else {
                throw dre;
            }
        });
    }

    /**
     * Returns a mapping function, which casts an Object response to the command response class.
     *
     * @param commandWithReadSubjects the original command.
     * @param hint used for logging purposes.
     * @return the mapping function.
     */
    @SuppressWarnings("unchecked")
    protected Function<Object, R> getResponseCaster(final C commandWithReadSubjects, final String hint) {

        return response -> {
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (R) response;
            } else if (response instanceof ErrorResponse) {
                throw ((ErrorResponse<?>) response).getDittoRuntimeException();
            } else if (response instanceof AskException || response instanceof AskTimeoutException) {
                final Optional<DittoRuntimeException> dittoRuntimeException =
                        handleAskTimeoutForCommand(commandWithReadSubjects, (Throwable) response);
                if (dittoRuntimeException.isPresent()) {
                    throw dittoRuntimeException.get();
                } else {
                    return null;
                }
            } else {
                throw reportErrorOrResponse(hint, response, null);
            }
        };
    }

    /**
     * Allows to wrap a command into something different before
     * {@link #ask(ActorRef,Signal, String, Scheduler, Executor) asking}.
     * Useful if the {@link ActorRef actor} that should be asked is the pubsub mediator and the command therefore needs
     * to be wrapped into {@link akka.cluster.pubsub.DistributedPubSubMediator.Send }.
     *
     * @param command command to wrap.
     * @return the wrapped command.
     */
    protected Object wrapBeforeAsk(final C command) {
        return command;
    }

    /**
     * Handles the {@link AskTimeoutException} when
     * {@link #ask(ActorRef,Signal, String, Scheduler, Executor) asking}
     * the given {@code command} by transforming it into an individual {@link DittoRuntimeException}.
     * May also respond with an empty Optional if no {@link DittoRuntimeException} should be thrown at all.
     *
     * @param command The command that was used to ask.
     * @param askTimeout the ask timeout exception.
     * @return the DittoRuntimeException or an empty Optional if no DittoRuntimeException should be thrown.
     */
    protected abstract Optional<DittoRuntimeException> handleAskTimeoutForCommand(C command, Throwable askTimeout);

    /**
     * Filters the given {@code commandResponse} by using the given {@code enforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param enforcer the enforcer that should be used for filtering.
     * @return the filtered command response.
     */
    public abstract R filterJsonView(R commandResponse, Enforcer enforcer);

}
