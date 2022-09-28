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
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;

/**
 * Policy together with its enforcer.
 */
@Immutable
public final class PolicyEnforcer {

    @Nullable private final Policy policy;
    private final Enforcer enforcer;

    private PolicyEnforcer(@Nullable final Policy policy, final Enforcer enforcer) {
        this.policy = policy;
        this.enforcer = enforcer;
    }

    /**
     * Create a policy enforcer from policy.
     *
     * @param policy the policy
     * @return the pair
     */
    public static CompletionStage<PolicyEnforcer> withResolvedImports(final Policy policy,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        return policy.withResolvedImports(policyResolver)
                .thenApply(resolvedPolicy -> {
                    final var enforcer = PolicyEnforcers.defaultEvaluator(resolvedPolicy);
                    return new PolicyEnforcer(resolvedPolicy, enforcer);
                });
    }

    /**
     * Create a policy together with its enforcer.
     *
     * @param policy the policy
     * @return the pair
     */
    public static PolicyEnforcer of(final Policy policy) {
        return new PolicyEnforcer(policy, PolicyEnforcers.defaultEvaluator(policy));
    }

    /**
     * Create a cache entry containing an Enforcer extracted from the passed {@code policyEnforcerEntry}.
     *
     * @param policyEnforcerEntry a {@link PolicyEnforcer} containing both policy and enforcer.
     * @return the cache entry containing an Enforcer.
     */
    public static Entry<Enforcer> project(final Entry<PolicyEnforcer> policyEnforcerEntry) {
        if (policyEnforcerEntry.exists()) {
            return Entry.of(policyEnforcerEntry.getRevision(), policyEnforcerEntry.getValueOrThrow().getEnforcer());
        } else {
            return Entry.nonexistent();
        }
    }

    /**
     * Create a cache entry containing a PolicyEnforcer without policy.
     *
     * @param enforcerEntry the enforcer cache entry.
     * @return the cache entry containing a PolicyEnforcer without policy
     */
    public static Entry<PolicyEnforcer> embed(final Entry<Enforcer> enforcerEntry) {
        if (enforcerEntry.exists()) {
            return Entry.of(enforcerEntry.getRevision(), new PolicyEnforcer(null, enforcerEntry.getValueOrThrow()));
        } else {
            return Entry.nonexistent();
        }
    }

    /**
     * Retrieve the policy.
     *
     * @return the policy.
     */
    public Optional<Policy> getPolicy() {
        return Optional.ofNullable(policy);
    }

    /**
     * Retrieve the enforcer.
     *
     * @return the enforcer.
     */
    public Enforcer getEnforcer() {
        return enforcer;
    }

}
