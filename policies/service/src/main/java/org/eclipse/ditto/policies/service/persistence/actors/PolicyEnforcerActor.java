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
package org.eclipse.ditto.policies.service.persistence.actors;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractEnforcerActor;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.eclipse.ditto.policies.service.enforcement.PolicyCommandEnforcement;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Enforcer responsible for enforcing {@link PolicyCommand}s and filtering {@link PolicyCommandResponse}s utilizing the
 * {@link PolicyCommandEnforcement}.
 */
public final class PolicyEnforcerActor
        extends AbstractEnforcerActor<PolicyId, PolicyCommand<?>, PolicyCommandResponse<?>, PolicyCommandEnforcement> {

    @SuppressWarnings("unused")
    private PolicyEnforcerActor(final PolicyId policyId,
            final PolicyCommandEnforcement policyCommandEnforcement,
            final ActorRef pubSubMediator,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        super(policyId, policyCommandEnforcement, pubSubMediator, blockedNamespaces);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param policyId the PolicyId this enforcer actor is responsible for.
     * @param policyCommandEnforcement the policy command enforcement logic to apply in the enforcer.
     * @param pubSubMediator the ActorRef of the distributed pub-sub-mediator used to subscribe for policy updates in
     * order to perform invalidations.
     * @param blockedNamespaces the blocked namespaces functionality to retrieve/subscribe for blocked namespaces.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final PolicyId policyId,
            final PolicyCommandEnforcement policyCommandEnforcement,
            final ActorRef pubSubMediator,
            @Nullable final BlockedNamespaces blockedNamespaces) {

        return Props.create(PolicyEnforcerActor.class, policyId, policyCommandEnforcement, pubSubMediator,
                blockedNamespaces);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement() {
        return CompletableFuture.completedStage(entityId);
    }

    @Override
    protected CompletionStage<PolicyEnforcer> providePolicyEnforcer(@Nullable final PolicyId policyId) {
        if (null == policyId) {
            return CompletableFuture.completedStage(null);
        } else {
            return Patterns.ask(getContext().getParent(), SudoRetrievePolicy.of(policyId,
                            DittoHeaders.newBuilder()
                                    .correlationId("sudoRetrievePolicyFromPolicyEnforcerActor-" + UUID.randomUUID())
                                    .build()
                    ), DEFAULT_LOCAL_ASK_TIMEOUT
            ).thenApply(response -> handleSudoRetrievePolicyResponse(response).orElse(null));
        }
    }

    @Override
    protected boolean shouldInvalidatePolicyEnforcerAfterEnforcement(final PolicyCommand<?> signal) {
        // this should always be done for modifying commands:
        return signal instanceof PolicyModifyCommand<?>;
        // TODO TJ optimization: only if the resources/subjects of the policy were changed
    }

    private static Optional<PolicyEnforcer> handleSudoRetrievePolicyResponse(final Object response) {
        if (response instanceof SudoRetrievePolicyResponse sudoRetrievePolicyResponse) {
            final var policy = sudoRetrievePolicyResponse.getPolicy();
            return Optional.of(PolicyEnforcer.of(policy, PolicyEnforcers.defaultEvaluator(policy)));
        } else if (response instanceof PolicyNotAccessibleException) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

}
