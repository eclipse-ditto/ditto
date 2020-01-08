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
package org.eclipse.ditto.services.models.signalenrichment;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheFactory;
import org.eclipse.ditto.services.utils.cache.CacheLookupContext;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cacheloaders.ThingEnrichmentCacheLoader;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;

import akka.actor.ActorRef;

/**
 * Retrieve fixed parts of things by asking an actor.
 */
public final class CachingSignalEnrichmentFacade implements SignalEnrichmentFacade {

    private final Cache<EntityIdWithResourceType, JsonObject> extraFieldsCache;

    private CachingSignalEnrichmentFacade(
            final ActorRef commandHandler,
            final Duration askTimeout,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor) {

        final AsyncCacheLoader<EntityIdWithResourceType, JsonObject> thingEnrichmentCacheLoader =
                new ThingEnrichmentCacheLoader(askTimeout, commandHandler);
        extraFieldsCache = CacheFactory.createCache(thingEnrichmentCacheLoader, cacheConfig,
                null, // explicitly disable metrics for this cache
                cacheLoaderExecutor);
    }

    /**
     * Create a thing-enriching facade that retrieves partial things by using a Caffeine cache.
     *
     * @param commandHandler the actor used to send "retrieve" signals.
     * @param askTimeout How long to wait for the async cache loader when loading enriched things.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @return The facade.
     * @throws NullPointerException if any argument is null.
     */
    public static CachingSignalEnrichmentFacade of(final ActorRef commandHandler, final Duration askTimeout,
            final CacheConfig cacheConfig, final Executor cacheLoaderExecutor) {
        return new CachingSignalEnrichmentFacade(commandHandler, askTimeout, cacheConfig, cacheLoaderExecutor);
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            final JsonFieldSelector jsonFieldSelector, final DittoHeaders dittoHeaders) {

        // TODO TJ somehow we need to add the signal which caused the "extra" fields retrieval here or in another
        //  method in order to update the cache when the revision number just was raised by 1


        // TODO TJ and somehow invalidation on policy update also has to be handled

        final JsonFieldSelector enhancedPointer = JsonFactory.newFieldSelectorBuilder()
                .addPointers(jsonFieldSelector)
                .addFieldDefinition(Thing.JsonFields.REVISION) // additionally always select the revision
                .build();
        final EntityIdWithResourceType idWithResourceType = EntityIdWithResourceType.of(
                ThingCommand.RESOURCE_TYPE, thingId, new CacheLookupContext(dittoHeaders, enhancedPointer));

        return extraFieldsCache.get(idWithResourceType)
                .thenApply(opt -> opt.orElse(JsonObject.empty()));
    }

}
