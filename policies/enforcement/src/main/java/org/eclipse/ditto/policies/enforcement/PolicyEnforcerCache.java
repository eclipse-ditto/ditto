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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.enforcement.config.NamespacePoliciesConfig;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import scala.concurrent.ExecutionContextExecutor;

final class PolicyEnforcerCache implements Cache<PolicyId, Entry<PolicyEnforcer>> {

    private final Cache<PolicyId, Entry<PolicyEnforcer>> delegate;
    private final Map<PolicyId, Set<PolicyId>> policyIdToImportingMap;
    private final NamespacePoliciesConfig namespacePoliciesConfig;

    PolicyEnforcerCache(final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader,
            final ExecutionContextExecutor cacheDispatcher,
            final CacheConfig cacheConfig,
            final NamespacePoliciesConfig namespacePoliciesConfig) {
        policyIdToImportingMap = new ConcurrentHashMap<>();
        this.namespacePoliciesConfig = namespacePoliciesConfig;
        this.delegate = CacheFactory.createCache(
                (policyId, executor) -> policyEnforcerCacheLoader.asyncLoad(policyId, executor)
                        .whenCompleteAsync(((policyEnforcerEntry, throwable) -> policyEnforcerEntry.get()
                                .flatMap(PolicyEnforcer::getPolicy)
                                .map(Policy::getPolicyImports)
                                .filter(imports -> !imports.isEmpty())
                                .ifPresent(imports -> imports.stream()
                                        .map(PolicyImport::getImportedPolicyId)
                                        .forEach(importedPolicyId -> policyIdToImportingMap.compute(
                                                importedPolicyId, (importedPolicyId1, importingPolicyIds) -> {
                                                    final Set<PolicyId> newImportingPolicyIds =
                                                            importingPolicyIds == null ? new HashSet<>() :
                                                                    importingPolicyIds;
                                                    newImportingPolicyIds.add(policyId);
                                                    return newImportingPolicyIds;
                                                })))
                                ),
                                cacheDispatcher
                        ),
                cacheConfig,
                "policy_enforcer_cache",
                cacheDispatcher
        );
    }

    @Override
    public CompletableFuture<Optional<Entry<PolicyEnforcer>>> get(final PolicyId key) {
        return delegate.get(key);
    }

    @Override
    public CompletableFuture<Optional<Entry<PolicyEnforcer>>> get(final PolicyId key,
            final Function<Throwable, Optional<Entry<PolicyEnforcer>>> errorHandler) {
        return delegate.get(key, errorHandler);
    }

    @Override
    public CompletableFuture<Optional<Entry<PolicyEnforcer>>> getIfPresent(final PolicyId key) {
        return delegate.getIfPresent(key);
    }

    @Override
    public Optional<Entry<PolicyEnforcer>> getBlocking(final PolicyId key) {
        return delegate.getBlocking(key);
    }

    @Override
    public boolean invalidate(final PolicyId policyId) {
        // Invalidate the changed policy itself
        final boolean directlyCached = delegate.invalidate(policyId);

        // Invalidate all policies that explicitly import the changed policy, and for each such
        // importer that is itself a namespace root, also invalidate its namespace dependents.
        // This covers the transitive case: imported-policy changes → root-policy (importer) is
        // invalidated → child policies in matching namespaces are also invalidated.
        final Set<PolicyId> importingPolicies =
                Optional.ofNullable(policyIdToImportingMap.remove(policyId)).orElseGet(Set::of);
        final boolean indirectlyCachedViaImport = importingPolicies.stream()
                .map(importingPolicyId -> {
                    final boolean importerInvalidated = delegate.invalidate(importingPolicyId);
                    final boolean namespaceDependentsInvalidated =
                            invalidateNamespaceDependents(importingPolicyId, delegate::invalidate);
                    return importerInvalidated || namespaceDependentsInvalidated;
                })
                .reduce((previous, next) -> previous || next)
                .orElse(false);

        // Invalidate all cached policies in namespaces that use the changed policy as a namespace root
        final boolean indirectlyCachedViaNamespaceRoot = invalidateNamespaceDependents(policyId,
                delegate::invalidate);

        return directlyCached || indirectlyCachedViaImport || indirectlyCachedViaNamespaceRoot;
    }

    @Override
    public boolean invalidateConditionally(final PolicyId policyId,
            final Predicate<Entry<PolicyEnforcer>> valueCondition) {
        // Invalidate the changed policy itself
        final boolean directlyCached = delegate.invalidateConditionally(policyId, valueCondition);

        // Invalidate all policies that explicitly import the changed policy, and for each such
        // importer that is itself a namespace root, also invalidate its namespace dependents.
        final Set<PolicyId> importingPolicies =
                Optional.ofNullable(policyIdToImportingMap.remove(policyId)).orElseGet(Set::of);
        final boolean indirectlyCachedViaImport = importingPolicies.stream()
                .map(importingPolicyId -> {
                    final boolean importerInvalidated =
                            delegate.invalidateConditionally(importingPolicyId, valueCondition);
                    final boolean namespaceDependentsInvalidated = invalidateNamespaceDependents(
                            importingPolicyId, p -> delegate.invalidateConditionally(p, valueCondition));
                    return importerInvalidated || namespaceDependentsInvalidated;
                })
                .reduce((previous, next) -> previous || next)
                .orElse(false);

        // Invalidate all cached policies in namespaces that use the changed policy as a namespace root
        final boolean indirectlyCachedViaNamespaceRoot = invalidateNamespaceDependents(policyId,
                p -> delegate.invalidateConditionally(p, valueCondition));

        return directlyCached || indirectlyCachedViaImport || indirectlyCachedViaNamespaceRoot;
    }

    private boolean invalidateNamespaceDependents(final PolicyId policyId,
            final Function<PolicyId, Boolean> invalidateFunction) {
        if (!namespacePoliciesConfig.getAllNamespaceRootPolicyIds().contains(policyId)) {
            return false;
        }

        final Set<String> affectedNamespaces = namespacePoliciesConfig.getNamespacesForRootPolicy(policyId);
        if (affectedNamespaces.isEmpty()) {
            return false;
        }

        // O(n) scan over the full cache — acceptable for typical deployments where namespace root policies
        // change infrequently. For very large caches with frequently-changing root policies, consider
        // tracking namespace membership at cache-load time (reverse map: namespace → cached PolicyIds).
        return delegate.asMap().keySet().stream()
                .filter(cachedPolicyId -> affectedNamespaces.stream()
                        .anyMatch(pattern -> NamespacePoliciesConfig.namespaceMatchesPattern(
                                cachedPolicyId.getNamespace(), pattern)))
                .map(invalidateFunction)
                .reduce((a, b) -> a || b)
                .orElse(false);
    }

    @Override
    public void put(final PolicyId key, final Entry<PolicyEnforcer> value) {
        delegate.put(key, value);
    }

    @Override
    public ConcurrentMap<PolicyId, Entry<PolicyEnforcer>> asMap() {
        return delegate.asMap();
    }

}
