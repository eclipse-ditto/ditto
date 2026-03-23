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

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> {

    public static final String ENFORCEMENT_CACHE_DISPATCHER = "enforcement-cache-dispatcher";

    private final PolicyCacheLoader delegate;
    private final Executor enforcementCacheExecutor;
    private final NamespacePoliciesConfig namespacePoliciesConfig;
    @Nullable
    private final CompletableFuture<Cache<PolicyId, Entry<PolicyEnforcer>>> cacheFuture;

    /**
     * Constructor.
     *
     * @param policyCacheLoader used to load the policies which should be transformed to a {@link PolicyEnforcer}.
     * @param actorSystem the actor system to use.
     * @param namespacePoliciesConfig the namespace root policies configuration.
     */
    public PolicyEnforcerCacheLoader(final PolicyCacheLoader policyCacheLoader, final ActorSystem actorSystem,
            final NamespacePoliciesConfig namespacePoliciesConfig) {

        this(policyCacheLoader, actorSystem, namespacePoliciesConfig, null);
    }

    /**
     * Constructor with self-referencing cache for root-policy import tracking.
     * When {@code cacheFuture} is provided, root policies are loaded through the cache
     * rather than directly via the policy shard region. This ensures that their import
     * declarations are tracked in the import map of {@link PolicyEnforcerCache}, so
     * that changes to a policy imported by a namespace root policy correctly cascade to
     * all child policies in the covered namespaces.
     *
     * @param policyCacheLoader used to load the policies.
     * @param actorSystem the actor system to use.
     * @param namespacePoliciesConfig the namespace root policies configuration.
     * @param cacheFuture completed with the wrapping {@link PolicyEnforcerCache} after construction.
     */
    PolicyEnforcerCacheLoader(final PolicyCacheLoader policyCacheLoader, final ActorSystem actorSystem,
            final NamespacePoliciesConfig namespacePoliciesConfig,
            @Nullable final CompletableFuture<Cache<PolicyId, Entry<PolicyEnforcer>>> cacheFuture) {

        delegate = policyCacheLoader;
        enforcementCacheExecutor = actorSystem.dispatchers().lookup(ENFORCEMENT_CACHE_DISPATCHER);
        this.namespacePoliciesConfig = namespacePoliciesConfig;
        this.cacheFuture = cacheFuture;
    }

    @Override
    public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final PolicyId policyId, final Executor executor) {

        final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver =
                policyIdToResolve -> {
                    if (cacheFuture != null &&
                            namespacePoliciesConfig.getAllNamespaceRootPolicyIds().contains(policyIdToResolve)) {
                        return cacheFuture.thenComposeAsync(
                                cache -> cache.get(policyIdToResolve)
                                        .thenApply(entry -> entry.flatMap(Entry::get)
                                                .flatMap(PolicyEnforcer::getPolicy)),
                                executor);
                    }
                    return delegate.asyncLoad(policyIdToResolve, executor).thenApply(Entry::get);
                };

        return delegate.asyncLoad(policyId, executor)
                .thenComposeAsync(policyEntry ->
                        evaluatePolicy(policyEntry, policyResolver), enforcementCacheExecutor
                );
    }

    private CompletionStage<Entry<PolicyEnforcer>> evaluatePolicy(final Entry<Policy> entry,
            final Function<PolicyId, CompletionStage<Optional<Policy>>> policyResolver) {
        if (entry.exists()) {
            final var revision = entry.getRevision();
            final var policy = entry.getValueOrThrow();
            return PolicyEnforcer.withResolvedImportsAndNamespacePolicies(policy, policyResolver,
                            namespacePoliciesConfig)
                    .thenApply(enforcer -> Entry.of(revision, enforcer));
        } else {
            return CompletableFuture.completedFuture(Entry.nonexistent());
        }
    }

}
