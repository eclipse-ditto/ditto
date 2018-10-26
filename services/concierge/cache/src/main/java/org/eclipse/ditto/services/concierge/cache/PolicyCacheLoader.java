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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.policies.PoliciesResourceType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.PolicyImport;
import org.eclipse.ditto.model.policies.PolicyRevision;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.cache.CacheInvalidationListener;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads a policy by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyCacheLoader
        implements AsyncCacheLoader<EntityId, Entry<Policy>>, CacheInvalidationListener<EntityId, Entry<Policy>> {

    private final ActorAskCacheLoader<Policy, Command> policyLoader;
    private final Map<EntityId, Set<EntityId>> policyIdsUsedInImports;
    private final Set<Consumer<EntityId>> invalidators;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param policiesShardRegionProxy the shard-region-proxy.
     */
    public PolicyCacheLoader(final Duration askTimeout, final ActorRef policiesShardRegionProxy) {
        requireNonNull(askTimeout);
        requireNonNull(policiesShardRegionProxy);

        final Function<String, Command> commandCreator =
                policyId -> SudoRetrievePolicy.of(policyId, DittoHeaders.empty());

        this.policyLoader =
                ActorAskCacheLoader.forShard(askTimeout, PolicyCommand.RESOURCE_TYPE, policiesShardRegionProxy,
                        commandCreator, this::sudoRetrievePolicyResponseToPolicy);
        policyIdsUsedInImports = new HashMap<>();
        invalidators = new HashSet<>();
    }

    /**
     * Registers a Consumer to call for when {@link org.eclipse.ditto.services.utils.cache.Cache} entries are
     * invalidated.
     *
     * @param invalidator the Consumer to call for cache invalidation.
     */
    public void registerCacheInvalidator(final Consumer<EntityId> invalidator) {
        invalidators.add(invalidator);
    }

    @Override
    public CompletableFuture<Entry<Policy>> asyncLoad(final EntityId key, final Executor executor) {
        return policyLoader.asyncLoad(key, executor);
    }

    private Entry<Policy> sudoRetrievePolicyResponseToPolicy(final Object response) {
        if (response instanceof SudoRetrievePolicyResponse) {
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse = (SudoRetrievePolicyResponse) response;
            final Policy policy = sudoRetrievePolicyResponse.getPolicy();
            handleInvalidationOfImportedPolicies(policy);
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(badPolicyResponse("no revision"));
            return Entry.of(revision, policy);
        } else if (response instanceof PolicyNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

    private void handleInvalidationOfImportedPolicies(final Policy policy) {
        policy.getId()
                .map(id -> EntityId.of(PoliciesResourceType.POLICY, id))
                .ifPresent(policyIdUsingImports ->
                        policy.getImports().ifPresent(imports ->
                                imports.stream()
                                        .map(PolicyImport::getImportedPolicyId)
                                        .map(id -> EntityId.of(PoliciesResourceType.POLICY, id))
                                        .forEach(importedPolicyId -> {
                                            final Set<EntityId> alreadyUsedImports =
                                                    policyIdsUsedInImports.getOrDefault(importedPolicyId,
                                                            new HashSet<>());
                                            alreadyUsedImports.add(policyIdUsingImports);
                                            policyIdsUsedInImports.put(importedPolicyId, alreadyUsedImports);
                                        })));
    }

    private static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }

    @Override
    public void onCacheEntryInvalidated(final EntityId key, @Nullable final Entry<Policy> value) {
        Optional.ofNullable(policyIdsUsedInImports.get(key))
                .ifPresent(affectedPoliciesUsingImportedPolicyId ->
                        affectedPoliciesUsingImportedPolicyId.forEach(id ->
                                invalidators.forEach(ivd -> ivd.accept(id))
                        )
                );
    }
}
