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
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheFactory;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import scala.concurrent.ExecutionContextExecutor;

final class PolicyEnforcerCache implements Cache<PolicyId, Entry<PolicyEnforcer>> {

    private final Cache<PolicyId, Entry<PolicyEnforcer>> delegate;
    private final Map<PolicyId, Set<PolicyId>> policyIdToImportingMap;

    PolicyEnforcerCache(final AsyncCacheLoader<PolicyId, Entry<PolicyEnforcer>> policyEnforcerCacheLoader,
            final ExecutionContextExecutor cacheDispatcher,
            final CacheConfig cacheConfig) {
        policyIdToImportingMap = new ConcurrentHashMap<>();
        this.delegate = CacheFactory.createCache(
                (policyId, executor) -> policyEnforcerCacheLoader.asyncLoad(policyId, executor)
                        .whenComplete(((policyEnforcerEntry, throwable) -> policyEnforcerEntry.get()
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
                                                }))))),
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
    public CompletableFuture<Optional<Entry<PolicyEnforcer>>> getIfPresent(final PolicyId key) {
        return delegate.getIfPresent(key);
    }

    @Override
    public Optional<Entry<PolicyEnforcer>> getBlocking(final PolicyId key) {
        return delegate.getBlocking(key);
    }

    @Override
    public boolean invalidate(final PolicyId policyId) {
        // Invalidate the changed policy
        final boolean directlyCached = delegate.invalidate(policyId);

        // Invalidate all policies that import the changed policy
        final boolean indirectlyCachedViaImport = Optional.ofNullable(policyIdToImportingMap.remove(policyId))
                .stream()
                .flatMap(Collection::stream)
                .map(delegate::invalidate)
                .reduce((previous, next) -> previous || next)
                .orElse(false);

        return directlyCached || indirectlyCachedViaImport;
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
