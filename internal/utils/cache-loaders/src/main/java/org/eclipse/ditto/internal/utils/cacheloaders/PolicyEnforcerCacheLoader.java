/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cacheloaders;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheInvalidationListener;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.PolicyImporter;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.enforcers.PolicyEnforcers;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.RemovalCause;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<EnforcementCacheKey, Entry<PolicyEnforcer>>,
        CacheInvalidationListener<EnforcementCacheKey, Entry<Enforcer>> {

    private final Cache<EnforcementCacheKey, Entry<Policy>> policyCache;
    private final Map<EnforcementCacheKey, Set<EnforcementCacheKey>> policyIdsUsedInImports;
    private final Set<Consumer<EnforcementCacheKey>> invalidators;

    /**
     * Constructor.
     *
     * @param policyCache policy cache to load policies from.
     */
    public PolicyEnforcerCacheLoader(final Cache<EnforcementCacheKey, Entry<Policy>> policyCache) {
        this.policyCache = checkNotNull(policyCache, "policyCache");
        policyIdsUsedInImports = new HashMap<>();
        invalidators = new HashSet<>();
    }

    /**
     * Registers a Consumer to call for when {@link org.eclipse.ditto.internal.utils.cache.Cache} entries are
     * invalidated.
     *
     * @param invalidator the Consumer to call for cache invalidation.
     */
    public void registerCacheInvalidator(final Consumer<EnforcementCacheKey> invalidator) {
        invalidators.add(invalidator);
    }

    @Override
    public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final EnforcementCacheKey key,
            final Executor executor) {
        return policyCache.get(key)
                .thenApply(optionalPolicyEntry -> optionalPolicyEntry
                        .filter(Entry::exists)
                        .map(entry -> {
                            final Policy initialPolicy = entry.getValueOrThrow();

                            rememberImportedPolicies(initialPolicy);

                            final Set<PolicyEntry> mergedPolicyEntriesSet =
                                    PolicyImporter.mergeImportedPolicyEntries(initialPolicy, this::loadPolicyBlocking);
                            final Policy mergedPolicy = initialPolicy.toBuilder()
                                    .setAll(mergedPolicyEntriesSet)
                                    .build();
                            final long revision = initialPolicy.getRevision().map(PolicyRevision::toLong)
                                    .orElseThrow(() -> new IllegalStateException("Bad loaded Policy: no revision"));
                            return Entry.of(revision, PolicyEnforcer.of(mergedPolicy,
                                    PolicyEnforcers.defaultEvaluator(mergedPolicyEntriesSet)));
                        })
                        .orElse(Entry.nonexistent())
                );
    }

    private void rememberImportedPolicies(final Policy policy) {
        policy.getEntityId().ifPresent(policyIdUsingImports ->
                policy.getImports().ifPresent(imports ->
                        imports.stream()
                                .map(PolicyImport::getImportedPolicyId)
                                .forEach(importedPolicyId -> {
                                    final EnforcementCacheKey importedPolicyIdKey = EnforcementCacheKey.of(importedPolicyId);
                                    final Set<EnforcementCacheKey> usedImports =
                                            policyIdsUsedInImports.getOrDefault(importedPolicyIdKey, new HashSet<>());
                                    usedImports.add(EnforcementCacheKey.of(policyIdUsingImports));
                                    policyIdsUsedInImports.put(importedPolicyIdKey, usedImports);
                                })
                )
        );
    }

    @Override
    public void onCacheEntryInvalidated(final EnforcementCacheKey key, @Nullable final Entry<Enforcer> value,
            final RemovalCause removalCause) {
        if (!removalCause.wasEvicted()) {
            Optional.ofNullable(policyIdsUsedInImports.get(key))
                    .ifPresent(usedImports ->
                            usedImports.forEach(id -> invalidators.forEach(invalidator -> invalidator.accept(id)))
                    );
        }
        // remove imports as when loading the policyEnforcer again in #loadAsync, the key is re-added
        policyIdsUsedInImports.remove(key);
    }

    private Optional<Policy> loadPolicyBlocking(final PolicyId policyId) {
        return policyCache.getBlocking(EnforcementCacheKey.of(policyId))
                .filter(Entry::exists)
                .map(Entry::getValueOrThrow);
    }

}
