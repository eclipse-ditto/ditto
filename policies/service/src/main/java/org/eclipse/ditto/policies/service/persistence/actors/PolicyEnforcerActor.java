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
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.enforcement.AbstractEnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.service.enforcement.PolicyCommandEnforcement;

import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * Enforcer responsible for enforcing {@link PolicyCommand}s and filtering {@link PolicyCommandResponse}s utilizing the
 * {@link PolicyCommandEnforcement}.
 */
public final class PolicyEnforcerActor
        extends AbstractEnforcerActor<PolicyId, PolicyCommand<?>, PolicyCommandResponse<?>, PolicyCommandEnforcement> {

    private static final String ENFORCEMENT_DISPATCHER = "enforcement-dispatcher";

    private final PolicyEnforcerProvider policyEnforcerProvider = policyId -> {
        if (null == policyId) {
            return CompletableFuture.completedStage(Optional.empty());
        } else {
            return Patterns.ask(getContext().getParent(), SudoRetrievePolicy.of(policyId,
                            DittoHeaders.newBuilder()
                                    .correlationId("sudoRetrievePolicyFromPolicyEnforcerActor-" + UUID.randomUUID())
                                    .build()
                    ), DEFAULT_LOCAL_ASK_TIMEOUT
            ).thenApply(PolicyEnforcerActor::handleSudoRetrievePolicyResponse);
        }
    };

    @SuppressWarnings("unused")
    private PolicyEnforcerActor(final PolicyId policyId, final PolicyCommandEnforcement policyCommandEnforcement) {
        super(policyId, policyCommandEnforcement);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param policyId the PolicyId this enforcer actor is responsible for.
     * @param policyCommandEnforcement the policy command enforcement logic to apply in the enforcer.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final PolicyId policyId, final PolicyCommandEnforcement policyCommandEnforcement) {
        return Props.create(PolicyEnforcerActor.class, policyId, policyCommandEnforcement)
                .withDispatcher(ENFORCEMENT_DISPATCHER);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement(final Signal<?> signal) {
        return CompletableFuture.completedStage(entityId);
    }

    @Override
    protected CompletionStage<Optional<PolicyEnforcer>> providePolicyEnforcer(@Nullable final PolicyId policyId) {
        return policyEnforcerProvider.getPolicyEnforcer(policyId);
    }

    @Override
    protected CompletionStage<Optional<PolicyEnforcer>> loadPolicyEnforcer(final Signal<?> signal) {
        if (signal instanceof CreatePolicy createPolicy) {
            return CompletableFuture.completedStage(Optional.of(PolicyEnforcer.of(createPolicy.getPolicy())));
        }
        return super.loadPolicyEnforcer(signal);
    }

    private static Optional<PolicyEnforcer> handleSudoRetrievePolicyResponse(final Object response) {
        if (response instanceof SudoRetrievePolicyResponse sudoRetrievePolicyResponse) {
            final var policy = sudoRetrievePolicyResponse.getPolicy();
            return Optional.of(PolicyEnforcer.of(policy));
        } else if (response instanceof PolicyNotAccessibleException) {
            return Optional.empty();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

}
