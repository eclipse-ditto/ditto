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
package org.eclipse.ditto.things.service.enforcement.pre;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.ActorAskCacheLoader;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;
import akka.actor.Scheduler;

/**
 * Cache loader used for Thing existence check in pre-enforcement.
 */
final class PreEnforcementThingIdCacheLoader implements
        AsyncCacheLoader<ThingId, Entry<PolicyId>> {

    private final ActorAskCacheLoader<PolicyId, Command<?>, ThingId> delegate;

    /**
     * Constructor.
     *
     * @param askWithRetryConfig the configuration for the "ask with retry" pattern applied for the cache loader.
     * @param scheduler the scheduler to use for the "ask with retry" for retries.
     * @param shardRegionProxy the shard-region-proxy.
     */
    public PreEnforcementThingIdCacheLoader(final AskWithRetryConfig askWithRetryConfig,
            final Scheduler scheduler,
            final ActorRef shardRegionProxy) {

        delegate = ActorAskCacheLoader.forShard(askWithRetryConfig,
                scheduler,
                ThingConstants.ENTITY_TYPE,
                shardRegionProxy,
                thingId -> SudoRetrieveThing.of(thingId, DittoHeaders.empty()),
                PreEnforcementThingIdCacheLoader::handleSudoRetrieveThingResponse);
    }

    @Override
    public CompletableFuture<Entry<PolicyId>> asyncLoad(final ThingId key, final Executor executor) {
        return delegate.asyncLoad(key, executor);
    }

    private static Entry<PolicyId> handleSudoRetrieveThingResponse(final Object response) {

        if (response instanceof SudoRetrieveThingResponse sudoRetrieveThingResponse) {
            final var thing = sudoRetrieveThingResponse.getThing();
            final long revision = thing.getRevision().map(ThingRevision::toLong)
                    .orElseThrow(badThingResponse("no revision"));
            final var policyId = thing.getPolicyId().orElseThrow(badThingResponse("no PolicyId"));
            return Entry.of(revision, policyId);
        } else if (response instanceof ThingNotAccessibleException) {
            return Entry.nonexistent();
        } else {
            throw new IllegalStateException("expect SudoRetrieveThingResponse, got: " + response);
        }
    }

    private static Supplier<RuntimeException> badThingResponse(final String message) {
        return () -> new IllegalStateException("Bad SudoRetrieveThingResponse: " + message);
    }
}
