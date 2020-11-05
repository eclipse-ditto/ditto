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

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.ErrorResponse;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;

public abstract class AbstractEnforcementWithAsk<C extends Signal<?>, R extends CommandResponse>
        extends AbstractEnforcement<C> {

    private final Class<R> responseClass;

    /**
     * Create an enforcement step from its context.
     *
     * @param context the context of the enforcement step.
     */
    protected AbstractEnforcementWithAsk(final Contextual<C> context, final Class<R> responseClass) {
        super(context);
        this.responseClass = responseClass;
    }


    protected CompletionStage<R> askAndBuildJsonView(
            final ActorRef actorToAsk,
            final C commandWithReadSubjects,
            final Enforcer enforcer) {

        return ask(actorToAsk, commandWithReadSubjects, "before building JsonView")
                .thenApply(response -> reportJsonViewForQueryResponse(response, enforcer));
    }

    protected Object wrapBeforeAsk(final C command) {
        return command;
    }

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
                        throw reportTimeoutException(commandWithReadSubjects, (AskTimeoutException) response);
                    } else if (error instanceof AskTimeoutException) {
                        throw reportTimeoutException(commandWithReadSubjects, (AskTimeoutException) error);
                    } else if (response instanceof ErrorResponse) {
                        throw ((ErrorResponse<?>) response).getDittoRuntimeException();
                    } else {
                        throw reportErrorOrResponse(hint, response, error);
                    }
                });
    }

    protected abstract DittoRuntimeException reportTimeoutException(C command, AskTimeoutException askTimeoutException);

    protected abstract R reportJsonViewForQueryResponse(R commandResponse, Enforcer enforcer);
}
