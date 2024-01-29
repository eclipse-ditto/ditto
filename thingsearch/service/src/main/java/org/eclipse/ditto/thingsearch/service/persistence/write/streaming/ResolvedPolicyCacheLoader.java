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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

final class ResolvedPolicyCacheLoader
        implements AsyncCacheLoader<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> {

    private final PolicyCacheLoader policyCacheLoader;
    private final CompletableFuture<Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>>> cacheFuture;

    ResolvedPolicyCacheLoader(final PolicyCacheLoader policyCacheLoader,
            final CompletableFuture<Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>>> cacheFuture) {
        this.policyCacheLoader = policyCacheLoader;
        this.cacheFuture = cacheFuture;
    }

    @Override
    public CompletableFuture<? extends Entry<Pair<Policy, Set<PolicyTag>>>> asyncLoad(
            final PolicyIdResolvingImports policyIdResolvingImports,
            final Executor executor) {

        return policyCacheLoader.asyncLoad(policyIdResolvingImports.policyId(), executor)
                .thenCompose(policyEntry -> {
                    if (policyEntry.exists()) {
                        final Policy policy = policyEntry.getValueOrThrow();
                        final long revision = policy.getRevision().map(PolicyRevision::toLong)
                                .orElseThrow(
                                        () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: no revision"));
                        final Set<PolicyTag> referencedPolicies = new HashSet<>();

                        if (policyIdResolvingImports.resolveImports()) {
                            return cacheFuture.thenCompose(cache ->
                                            resolvePolicyImports(cache, policy, referencedPolicies)
                                    )
                                    .thenApply(resolvedPolicy ->
                                            Entry.of(revision, new Pair<>(resolvedPolicy, referencedPolicies))
                                    );
                        } else {
                            return CompletableFuture.completedFuture(
                                    Entry.of(revision, new Pair<>(policy, referencedPolicies))
                            );
                        }
                    } else {
                        return CompletableFuture.completedFuture(Entry.nonexistent());
                    }
                });
    }

    private static CompletionStage<Policy> resolvePolicyImports(
            final Cache<PolicyIdResolvingImports, Entry<Pair<Policy, Set<PolicyTag>>>> cache,
            final Policy policy,
            final Set<PolicyTag> referencedPolicies) {

        return policy.withResolvedImports(importedPolicyId ->
                cache.get(new PolicyIdResolvingImports(importedPolicyId, false)) // don't transitively resolve imports, only 1 "level"
                        .thenApply(entry -> entry.flatMap(Entry::get))
                        .thenApply(optionalReferencedPolicy -> {
                            if (optionalReferencedPolicy.isPresent()) {
                                final Policy referencedPolicy = optionalReferencedPolicy.get().first();
                                final Optional<PolicyRevision> refRevision = referencedPolicy.getRevision();
                                final Optional<PolicyId> entityId = referencedPolicy.getEntityId();
                                if (refRevision.isPresent() && entityId.isPresent()) {
                                    referencedPolicies.add(PolicyTag.of(entityId.get(), refRevision.get().toLong()));
                                }
                            }
                            return optionalReferencedPolicy.map(Pair::first);
                        })
        );
    }

}
