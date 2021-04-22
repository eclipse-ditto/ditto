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
package org.eclipse.ditto.services.utils.cacheloaders;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.model.enforcers.PolicyEnforcers;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.utils.cache.CacheKey;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.services.utils.cache.entry.Entry;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Loads a policy-enforcer by asking the policies shard-region-proxy.
 */
@Immutable
public final class PolicyEnforcerCacheLoader implements AsyncCacheLoader<CacheKey, Entry<PolicyEnforcer>> {

    private final ActorAskCacheLoader<PolicyEnforcer, Command<?>> delegate;

    /**
     * Constructor.
     *
     * @param askTimeout the ask-timeout for communicating with the shard-region-proxy.
     * @param policiesShardRegionProxy the shard-region-proxy.
     */
    public PolicyEnforcerCacheLoader(final Duration askTimeout, final ActorRef policiesShardRegionProxy) {
        requireNonNull(askTimeout);
        requireNonNull(policiesShardRegionProxy);

        final BiFunction<EntityId, CacheLookupContext, Command<?>> commandCreator =
                PolicyCommandFactory::sudoRetrievePolicy;
        final BiFunction<Object, CacheLookupContext, Entry<PolicyEnforcer>> responseTransformer =
                PolicyEnforcerCacheLoader::handleSudoRetrievePolicyResponse;

        delegate = ActorAskCacheLoader.forShard(askTimeout, PolicyConstants.ENTITY_TYPE, policiesShardRegionProxy,
                commandCreator, responseTransformer);
    }

    @Override
    public CompletableFuture<Entry<PolicyEnforcer>> asyncLoad(final CacheKey key,
            final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private static Entry<PolicyEnforcer> handleSudoRetrievePolicyResponse(final Object response,
            @Nullable final CacheLookupContext cacheLookupContext) {
        if (response instanceof SudoRetrievePolicyResponse) {
            final SudoRetrievePolicyResponse sudoRetrievePolicyResponse = (SudoRetrievePolicyResponse) response;
            final Policy policy = sudoRetrievePolicyResponse.getPolicy();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(() -> new IllegalStateException("Bad SudoRetrievePolicyResponse: no revision"));
            return Entry.of(revision, PolicyEnforcer.of(policy, PolicyEnforcers.defaultEvaluator(policy)));
        } else if (response instanceof PolicyNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrievePolicyResponse, got: " + response);
        }
    }

}
