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

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractEnforcerActor;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.enforcement.CreationRestrictionEnforcer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommandResponse;
import org.eclipse.ditto.policies.service.enforcement.PolicyCommandEnforcement;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;

/**
 * TODO TJ add javadoc
 */
public final class PolicyEnforcerActor
        extends AbstractEnforcerActor<PolicyId, PolicyCommand<?>, PolicyQueryCommandResponse<?>> {

    @SuppressWarnings("unused")
    private PolicyEnforcerActor(final PolicyId policyId,
            final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final ActorRef pubSubMediator) {

        super(policyId, new PolicyCommandEnforcement(creationRestrictionEnforcer), pubSubMediator);
    }

    /**
     * TODO TJ doc
     */
    public static Props props(final PolicyId policyId, final CreationRestrictionEnforcer creationRestrictionEnforcer,
            final ActorRef pubSubMediator) {
        return Props.create(PolicyEnforcerActor.class, policyId, creationRestrictionEnforcer, pubSubMediator);
    }

    @Override
    protected CompletionStage<PolicyId> getPolicyIdForEnforcement() {
        return CompletableFuture.completedStage(entityId);
    }

    @Override
    protected CompletionStage<PolicyEnforcer> loadPolicyEnforcer(final PolicyId policyId) {
        return Patterns.ask(getContext().getParent(), SudoRetrievePolicy.of(policyId,
                DittoHeaders.newBuilder()
                        .correlationId("sudoRetrievePolicyFromPolicyPersistenceEnforcerActor-" + UUID.randomUUID())
                        .build()
                ), DEFAULT_LOCAL_ASK_TIMEOUT
        ).thenApply(response -> handleSudoRetrievePolicyResponse(response).orElse(null));
    }

    /**
     * TODO TJ copied over from PolicyEnforcerCacheLoader - simplify!
     * @param response
     * @return
     */
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
