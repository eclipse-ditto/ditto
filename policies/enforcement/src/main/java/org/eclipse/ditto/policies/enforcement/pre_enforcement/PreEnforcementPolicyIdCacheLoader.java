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
package org.eclipse.ditto.policies.enforcement.pre_enforcement;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.api.persistence.PersistenceLifecycle;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.ActorAskCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementContext;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.policies.api.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.policies.model.PolicyConstants;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyRevision;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

public class PreEnforcementPolicyIdCacheLoader implements
        AsyncCacheLoader<EnforcementCacheKey, Entry<EnforcementCacheKey>> {

    private final ActorAskCacheLoader<EnforcementCacheKey, Command<?>, EnforcementContext> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param shardRegionProxy the shard-region-proxy.
     */
    public PreEnforcementPolicyIdCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef shardRegionProxy) {

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig,
                scheduler,
                PolicyConstants.ENTITY_TYPE,
                shardRegionProxy,
                (entityId, enforcementContext) -> SudoRetrievePolicy.of((PolicyId) entityId, DittoHeaders.empty()),
                PreEnforcementPolicyIdCacheLoader::handleSudoRetrievePolicyResponse);
    }

    @Override
    public CompletableFuture<Entry<EnforcementCacheKey>> asyncLoad(final EnforcementCacheKey key,
            final Executor executor) {

        return delegate.asyncLoad(key, executor);
    }

    private static Entry<EnforcementCacheKey> handleSudoRetrievePolicyResponse(final Object response,
            @Nullable final EnforcementContext context) {

        if (response instanceof SudoRetrievePolicyResponse) {
            final var sudoRetrievePolicyResponse = (SudoRetrievePolicyResponse) response;
            final var policy = sudoRetrievePolicyResponse.getPolicy();
            final long revision = policy.getRevision().map(PolicyRevision::toLong)
                    .orElseThrow(badPolicyResponse("no revision"));
            final var policyId = policy.getEntityId().orElseThrow(badPolicyResponse("no PolicyId"));
            final PersistenceLifecycle persistenceLifecycle =
                    policy.getLifecycle().map(Enum::name).flatMap(PersistenceLifecycle::forName).orElse(null);
            final EnforcementContext newEnforcementContext = EnforcementContext.of(persistenceLifecycle);
            final var resourceKey = EnforcementCacheKey.of(policyId, newEnforcementContext);
            return Entry.of(revision, resourceKey);
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
