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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.AbstractPolicyLoadingEnforcerActor;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.service.enforcement.PolicyCommandEnforcement;

import akka.actor.Props;

/**
 * Enforcer responsible for enforcing {@link PolicyCommand}s and filtering {@link PolicyCommandResponse}s utilizing the
 * {@link PolicyCommandEnforcement}.
 */
public final class PolicyEnforcerActor extends
        AbstractPolicyLoadingEnforcerActor<PolicyId, Signal<?>, PolicyCommandResponse<?>, PolicyCommandEnforcement> {

    private static final String ENFORCEMENT_DISPATCHER = "enforcement-dispatcher";

    @SuppressWarnings("unused")
    private PolicyEnforcerActor(final PolicyId policyId, final PolicyCommandEnforcement policyCommandEnforcement,
            final PolicyEnforcerProvider policyEnforcerProvider) {
        super(policyId, policyCommandEnforcement, policyEnforcerProvider);
    }

    /**
     * Creates Akka configuration object Props for this Actor.
     *
     * @param policyId the PolicyId this enforcer actor is responsible for.
     * @param policyCommandEnforcement the policy command enforcement logic to apply in the enforcer.
     * @param policyEnforcerProvider the policy enforcer provider.
     * @return the {@link Props} to create this actor.
     */
    public static Props props(final PolicyId policyId, final PolicyCommandEnforcement policyCommandEnforcement,
            final PolicyEnforcerProvider policyEnforcerProvider) {
        return Props.create(PolicyEnforcerActor.class, policyId, policyCommandEnforcement, policyEnforcerProvider)
                .withDispatcher(ENFORCEMENT_DISPATCHER);
    }

    @Override
    protected CompletionStage<PolicyId> providePolicyIdForEnforcement(final Signal<?> signal) {
        return CompletableFuture.completedStage(entityId);
    }

    @Override
    protected CompletionStage<Optional<PolicyEnforcer>> loadPolicyEnforcer(final Signal<?> signal) {
        if (signal instanceof CreatePolicy createPolicy) {
            final PolicyCacheLoader policyCacheLoader = PolicyCacheLoader.getSingletonInstance(getContext().system());
            final Function<PolicyId, CompletionStage<Optional<Policy>>> importedPolicyResolver =
                    importedPolicyId -> policyCacheLoader.asyncLoad(importedPolicyId, getContext().dispatcher())
                            .thenApply(Entry::get);
            return PolicyEnforcer.withResolvedImports(createPolicy.getPolicy(), importedPolicyResolver)
                    .thenApply(Optional::of);
        }
        return super.loadPolicyEnforcer(signal);
    }

}
