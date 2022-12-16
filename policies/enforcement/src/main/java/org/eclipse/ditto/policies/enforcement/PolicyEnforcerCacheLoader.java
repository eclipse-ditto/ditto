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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> {

    public static final String ENFORCEMENT_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    private final PolicyCacheLoader delegate;

    /**
     * Constructor.
     *
     * @param policyCacheLoader used to load the policies which should be transformed to a {@link PolicyEnforcer}.
     */
    public PolicyEnforcerCacheLoader(final PolicyCacheLoader policyCacheLoader) {

        delegate = policyCacheLoader;
    }

    @Override
    public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final PolicyId policyId, final Executor executor) {

        final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver =
                policyIdToResolve -> delegate.asyncLoad(policyIdToResolve, executor).thenApply(Entry::get);

        return delegate.asyncLoad(policyId, executor)
                .thenCompose(policyEntry -> evaluatePolicy(policyEntry, policyResolver));
    }

    private CompletionStage<Entry<PolicyEnforcer>> evaluatePolicy(final Entry<Policy> entry,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        if (entry.exists()) {
            final var revision = entry.getRevision();
            final var policy = entry.getValueOrThrow();
            return PolicyEnforcer.withResolvedImports(policy, policyResolver)
                    .thenApply(enforcer -> Entry.of(revision, enforcer));
        } else {
            return CompletableFuture.completedStage(Entry.nonexistent());
        }
    }

}
