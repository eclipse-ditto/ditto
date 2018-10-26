/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.cache;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.PolicyImportHelper;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<EntityId, Entry<Enforcer>> {

    private final Cache<EntityId, Entry<Policy>> policyCacheLoader;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param policyCacheLoader the ....
     */
    public PolicyEnforcerCacheLoader(final Duration askTimeout,
            final Cache<EntityId, Entry<Policy>> policyCacheLoader) {
        requireNonNull(askTimeout);
        this.policyCacheLoader = requireNonNull(policyCacheLoader);
    }

    @Override
    public CompletableFuture<Entry<Enforcer>> asyncLoad(final EntityId key, final Executor executor) {
        return policyCacheLoader.get(key).thenApply(optionalPolicyEntry ->
                optionalPolicyEntry
                        .map(this::handleSudoRetrievePolicyResponse)
                        .orElseGet(Entry::nonexistent)
        );
    }

    private Entry<Enforcer> handleSudoRetrievePolicyResponse(final Entry<Policy> policyEntry) {
        if (policyEntry.exists()) {
            final Policy policy = policyEntry.getValue();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(badPolicyResponse("no revision"));
            final Set<PolicyEntry> mergedPolicyEntriesSet =
                    PolicyImportHelper.mergeImportedPolicyEntries(policy, this::policyLoader);
            return Entry.of(revision, PolicyEnforcers.defaultEvaluator(mergedPolicyEntriesSet));
        } else {
            return Entry.nonexistent();
        }
    }

    private Optional<Policy> policyLoader(final String policyId) {
        return policyCacheLoader.getBlocking(EntityId.of(PolicyCommand.RESOURCE_TYPE, policyId))
                .filter(Entry::exists)
                .map(Entry::getValue);
    }

    private static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }

}
