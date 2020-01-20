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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CacheFactory;
import org.eclipse.ditto.services.utils.cache.EntityIdWithResourceType;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.signals.commands.things.ThingCommand;

/**
 * Retrieve additional parts of things by asking an asynchronous cache.
 * Instantiated once per cluster node so that it builds up a cache across all signal enrichments on a local cluster
 * node.
 */
public final class CachingSignalEnrichmentFacade implements SignalEnrichmentFacade {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(CachingSignalEnrichmentFacade.class);

    private final Cache<EntityIdWithResourceType, JsonObject> extraFieldsCache;

    private CachingSignalEnrichmentFacade(
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        extraFieldsCache = CacheFactory.createCache(
                SignalEnrichmentCacheLoader.of(cacheLoaderFacade),
                cacheConfig,
                cacheNamePrefix + "_signal_enrichment_cache",
                cacheLoaderExecutor);
    }

    /**
     * Create a signal-enriching facade that retrieves partial things by using a Caffeine cache.
     *
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @return The facade.
     * @throws NullPointerException if any argument is null.
     */
    public static CachingSignalEnrichmentFacade of(final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig, final Executor cacheLoaderExecutor, final String cacheNamePrefix) {

        return new CachingSignalEnrichmentFacade(cacheLoaderFacade, cacheConfig, cacheLoaderExecutor,
                cacheNamePrefix);
    }

    @Override
    public CompletionStage<JsonObject> retrievePartialThing(final ThingId thingId,
            final JsonFieldSelector jsonFieldSelector, final DittoHeaders dittoHeaders) {

        final EntityIdWithResourceType idWithResourceType =
                EntityIdWithResourceType.of(ThingCommand.RESOURCE_TYPE, thingId,
                        CacheFactory.newCacheLookupContext(dittoHeaders, jsonFieldSelector));

        return doCacheLookup(idWithResourceType, dittoHeaders);
    }

    private CompletableFuture<JsonObject> doCacheLookup(final EntityIdWithResourceType idWithResourceType,
            final DittoHeaders dittoHeaders) {

        LOGGER.withCorrelationId(dittoHeaders)
                .debug("Looking up cache entry for <{}>", idWithResourceType);
        return extraFieldsCache.get(idWithResourceType)
                .thenApply(optionalJsonObject -> optionalJsonObject.orElseGet(JsonObject::empty));
    }

}
