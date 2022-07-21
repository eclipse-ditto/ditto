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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.ActorAskCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Loads a policy by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyCacheLoader implements AsyncCacheLoader<PolicyId, Entry<Policy>> {

    private final ActorAskCacheLoader<Policy, Command<?>, PolicyId> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param policiesShardRegionProxy the shard-region-proxy.
     */
    public PolicyCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef policiesShardRegionProxy) {

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig,
                scheduler,
                PolicyConstants.ENTITY_TYPE,
                policiesShardRegionProxy,
                PolicyCommandFactory::sudoRetrievePolicy,
                PolicyCacheLoader::extractPolicy);
    }

    @Override
    public CompletableFuture<Entry<Policy>> asyncLoad(final PolicyId policyId, final Executor executor) {
        return delegate.asyncLoad(policyId, executor);
    }

    private static Entry<Policy> extractPolicy(final Object response) {
        if (response instanceof final SudoRetrievePolicyResponse sudoRetrievePolicyResponse) {
            final var policy = sudoRetrievePolicyResponse.getPolicy();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(() -> new IllegalStateException("Bad SudoRetrievePolicyResponse: no revision"));
            return Entry.of(revision, policy);
        } else if (response instanceof PolicyNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

}
