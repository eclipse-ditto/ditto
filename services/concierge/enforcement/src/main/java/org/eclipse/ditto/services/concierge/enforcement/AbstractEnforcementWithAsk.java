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
package org.eclipse.ditto.services.concierge.enforcement;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.ErrorResponse;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

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
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    protected CompletionStage<R> askAndBuildJsonView(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final Enforcer enforcer) {

        return ask(actorToAsk, commandWithReadSubjects, "before building JsonView")
                .thenApply(response -> filterJsonView(response, enforcer));
    }

    /**
     * Asks the given {@code actorToAsk} for a response by telling {@code commandWithReadSubjects}.
     *
     * @param actorToAsk the actor that should be asked.
     * @param commandWithReadSubjects the command that is used to ask.
     * @param hint used for logging purposes.
     * @return A completion stage which either completes with a filtered response of type {@link R} or fails with a
     * {@link DittoRuntimeException}.
     */
    @SuppressWarnings("unchecked") // We can ignore this warning since it is tested that response class is assignable
    protected CompletionStage<R> ask(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final String hint) {

        return Patterns.ask(actorToAsk, wrapBeforeAsk(commandWithReadSubjects), getAskTimeout())
                .handle((response, error) ->
                {
                    if (response != null && responseClass.isAssignableFrom(response.getClass())) {
                        return (R) response;
                    } else if (response instanceof AskTimeoutException) {
                        throw handleAskTimeoutForCommand(commandWithReadSubjects, (AskTimeoutException) response);
                    } else if (error instanceof AskTimeoutException) {
                        throw handleAskTimeoutForCommand(commandWithReadSubjects, (AskTimeoutException) error);
                    } else if (response instanceof ErrorResponse) {
                        throw ((ErrorResponse<?>) response).getDittoRuntimeException();
                    } else {
                        throw reportErrorOrResponse(hint, response, error);
                    }
                });
    }

    /**
     * Allows to wrap an command into something different before {@link #ask(ActorRef, Signal, String) asking}.
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
     * Handles the {@link AskTimeoutException} when {@link #ask(ActorRef, Signal, String) asking} the given
     * {@code command} by transforming it into a individual {@link DittoRuntimeException}.
     *
     * @param command The command that was used to ask.
     * @param askTimeout the ask timeout exception.
     * @return the ditto runtime exception.
     */
    protected abstract DittoRuntimeException handleAskTimeoutForCommand(C command, AskTimeoutException askTimeout);

    /**
     * Filters the given {@code commandResponse} by using the given {@code enforcer}.
     *
     * @param commandResponse the command response that needs  to be filtered.
     * @param enforcer the enforcer that should be used for filtering.
     * @return the filtered command response.
     */
    protected abstract R filterJsonView(R commandResponse, Enforcer enforcer);

}
