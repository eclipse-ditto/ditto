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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommand;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.connectivity.service.enforcement.ConnectivityCommandEnforcement;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.PolicyId;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Enforcer responsible for enforcing {@link ConnectivityCommand}s and filtering {@link ConnectivityCommandResponse}s
 * utilizing the {@link ConnectivityCommandEnforcement}.
 */
public final class ConnectionEnforcerActor
        extends AbstractEnforcerActor<ConnectionId, ConnectivityCommand<?>, ConnectivityCommandResponse<?>, ConnectivityCommandEnforcement> {

    @SuppressWarnings("unused")
    private ConnectionEnforcerActor(final ConnectionId connectionId,
            final ConnectivityCommandEnforcement connectivityCommandEnforcement,
            final ActorRef pubSubMediator) {

        super(connectionId, connectivityCommandEnforcement, pubSubMediator, null);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param connectionId the ConnectionId this enforcer actor is responsible for.
     * @param connectivityCommandEnforcement the connectivity command enforcement logic to apply in the enforcer.
     * @param pubSubMediator the ActorRef of the distributed pub-sub-mediator used to subscribe for policy updates in
     * order to perform invalidations.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final ConnectionId connectionId,
            final ConnectivityCommandEnforcement connectivityCommandEnforcement,
            final ActorRef pubSubMediator) {

        return Props.create(ConnectionEnforcerActor.class, connectionId, connectivityCommandEnforcement,
                pubSubMediator);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement() {
        // TODO CR-11344 implement
        return CompletableFuture.completedStage(null);
    }

    @Override
    protected CompletionStage<PolicyEnforcer> providePolicyEnforcer(@Nullable final PolicyId policyId) {
        // TODO CR-11344 implement
        return CompletableFuture.completedStage(null);
    }

    @Override
    protected boolean shouldInvalidatePolicyEnforcerAfterEnforcement(final Signal<?> signal) {
        // TODO CR-11344 implement
        return false;
    }

}
