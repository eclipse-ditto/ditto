/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.base.model.exceptions.AskException;
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
                .thenApply(response -> filterJsonView(response, enforcer));
    }

    @SuppressWarnings("unchecked")
    protected CompletionStage<R> askAndBuildJsonView(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final Object publish,
            final Enforcer enforcer,
            final Scheduler scheduler,
            final Executor executor) {

        return ask(actorToAsk, commandWithReadSubjects, publish, "before building JsonView", scheduler, executor)
                .thenApply(response -> filterJsonView((R) response.setDittoHeaders(
                        response.getDittoHeaders()
                                .toBuilder()
                                .authorizationContext(commandWithReadSubjects.getDittoHeaders().getAuthorizationContext())
                                .build()
                ), enforcer));
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code commandWithReadSubjects}.
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

        final var publish = wrapBeforeAsk(commandWithReadSubjects);
        return ask(actorToAsk, commandWithReadSubjects, publish, hint, scheduler, executor);
    }

    @SuppressWarnings("unchecked") // We can ignore this warning since it is tested that response class is assignable
    protected CompletionStage<R> ask(
            final ActorRef actorToAsk,
            final C signal,
            final Object publish,
            final String hint,
            final Scheduler scheduler,
            final Executor executor) {

        return AskWithRetry.askWithRetry(actorToAsk, publish,
                getAskWithRetryConfig(), scheduler, executor,
                response -> {
                    if (responseClass.isAssignableFrom(response.getClass())) {
                        return (R) response;
                    } else if (response instanceof ErrorResponse) {
                        throw ((ErrorResponse<?>) response).getDittoRuntimeException();
                    } else if (response instanceof AskException) {
                        throw handleAskTimeoutForCommand(signal, (Throwable) response);
                    } else if (response instanceof AskTimeoutException) {
                        throw handleAskTimeoutForCommand(signal, (Throwable) response);
                    } else {
                        throw reportErrorOrResponse(hint, response, null);
                    }
                }
        ).exceptionally(throwable -> {
            final DittoRuntimeException dre = DittoRuntimeException.asDittoRuntimeException(throwable, cause ->
                    AskException.newBuilder()
                            .dittoHeaders(signal.getDittoHeaders())
                            .cause(cause)
                            .build());
            if (dre instanceof AskException) {
                throw handleAskTimeoutForCommand(signal, throwable);
            } else {
                throw dre;
            }
        });
    }

    /**
     * Allows to wrap an command into something different before
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
     * the given {@code command} by transforming it into a individual {@link DittoRuntimeException}.
     *
     * @param command The command that was used to ask.
     * @param askTimeout the ask timeout exception.
     * @return the ditto runtime exception.
     */
    protected abstract DittoRuntimeException handleAskTimeoutForCommand(C command, Throwable askTimeout);

    /**
     * Filters the given {@code commandResponse} by using the given {@code enforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param enforcer the enforcer that should be used for filtering.
     * @return the filtered command response.
     */
    protected abstract R filterJsonView(R commandResponse, Enforcer enforcer);

}
