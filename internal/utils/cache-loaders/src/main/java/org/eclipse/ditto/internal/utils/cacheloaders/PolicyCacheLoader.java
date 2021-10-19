/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Loads a policy by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyCacheLoader implements AsyncCacheLoader<EnforcementCacheKey, Entry<Policy>> {

    private final ActorAskCacheLoader<Policy, Command<?>, EnforcementContext> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param policiesShardRegionProxy the shard-region-proxy.
     */
    public PolicyCacheLoader(final AskWithRetryConfig askWithRetryConfig, final Scheduler scheduler,
            final ActorRef policiesShardRegionProxy) {
        requireNonNull(askWithRetryConfig);
        requireNonNull(policiesShardRegionProxy);

        final BiFunction<EntityId, EnforcementContext, Command<?>> commandCreator =
                PolicyCommandFactory::sudoRetrievePolicy;
        final BiFunction<Object, EnforcementContext, Entry<Policy>> responseTransformer =
                this::sudoRetrievePolicyResponseToPolicy;

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig, scheduler, PolicyConstants.ENTITY_TYPE,
                policiesShardRegionProxy, commandCreator, responseTransformer);
    }

    @Override
    public CompletableFuture<Entry<Policy>> asyncLoad(final EnforcementCacheKey key,
            final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private Entry<Policy> sudoRetrievePolicyResponseToPolicy(final Object response,
            @Nullable final EnforcementContext cacheLookupContext) {
        if (response instanceof SudoRetrievePolicyResponse) {
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse = (SudoRetrievePolicyResponse) response;
            final Policy policy = sudoRetrievePolicyResponse.getPolicy();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(badPolicyResponse("no revision"));
            return Entry.of(revision, policy);
        } else if (response instanceof PolicyNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badPolicyResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrievePolicyResponse: " + message);
    }

}
