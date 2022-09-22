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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.PolicyCacheLoader;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

final class ResolvedPolicyCacheLoader implements AsyncCacheLoader<PolicyId, Entry<Policy>> {

    private final PolicyCacheLoader policyCacheLoader;

    ResolvedPolicyCacheLoader(final PolicyCacheLoader policyCacheLoader) {
        this.policyCacheLoader = policyCacheLoader;
    }

    @Override
    public CompletableFuture<? extends Entry<Policy>> asyncLoad(final PolicyId policyId, final Executor executor) {
        return policyCacheLoader.asyncLoad(policyId, executor)
                .thenApply(policyEntry -> {
                    if (policyEntry.exists()) {
                        final Policy policy = policyEntry.getValueOrThrow();
                        final Policy resolvedPolicy = policy.withResolvedImports(
                                importedPolicyId -> policyCacheLoader.asyncLoad(importedPolicyId, executor)
                                        .toCompletableFuture()
                                        .join()
                                        .get());
                        final long revision = policy.getRevision().map(PolicyRevision::toLong)
                                .orElseThrow(() -> new IllegalStateException("Bad SudoRetrievePolicyResponse: no revision"));
                        return Entry.of(revision, resolvedPolicy);
                    } else {
                        return policyEntry;
                    }
                });
    }

}
