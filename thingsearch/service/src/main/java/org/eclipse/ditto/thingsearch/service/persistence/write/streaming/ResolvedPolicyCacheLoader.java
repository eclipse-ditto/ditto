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
import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.japi.Pair;

final class ResolvedPolicyCacheLoader implements AsyncCacheLoader<PolicyId, Entry<Pair<Policy, Set<PolicyTag>>>> {

    private final PolicyCacheLoader policyCacheLoader;

    ResolvedPolicyCacheLoader(final PolicyCacheLoader policyCacheLoader) {
        this.policyCacheLoader = policyCacheLoader;
    }

    @Override
    public CompletableFuture<? extends Entry<Pair<Policy, Set<PolicyTag>>>> asyncLoad(final PolicyId policyId,
            final Executor executor) {

        return policyCacheLoader.asyncLoad(policyId, executor)
                .thenCompose(policyEntry -> {
                    if (policyEntry.exists()) {
                        final Policy policy = policyEntry.getValueOrThrow();
                        final Set<PolicyTag> referencedPolicies = new HashSet<>();
                        return policy.withResolvedImports(
                                        importedPolicyId -> policyCacheLoader.asyncLoad(importedPolicyId, executor)
                                                .thenApply(Entry::get)
                                                .thenApply(optionalReferencedPolicy -> {
                                                    if (optionalReferencedPolicy.isPresent()) {
                                                        final Policy referencedPolicy =
                                                                optionalReferencedPolicy.get();
                                                        final Optional<PolicyRevision> revision =
                                                                referencedPolicy.getRevision();
                                                        final Optional<PolicyId> entityId =
                                                                referencedPolicy.getEntityId();
                                                        if (revision.isPresent() && entityId.isPresent()) {
                                                            referencedPolicies.add(
                                                                    PolicyTag.of(entityId.get(),
                                                                            revision.get().toLong())
                                                            );
                                                        }
                                                    }
                                                    return optionalReferencedPolicy;
                                                }))
                                .thenApply(resolvedPolicy -> {
                                    final long revision = policy.getRevision().map(PolicyRevision::toLong)
                                            .orElseThrow(
                                                    () -> new IllegalStateException(
                                                            "Bad SudoRetrievePolicyResponse: no revision"));
                                    return Entry.of(revision, new Pair<>(resolvedPolicy, referencedPolicies));
                                });

                    } else {
                        return CompletableFuture.completedFuture(Entry.nonexistent());
                    }
                });
    }

}
