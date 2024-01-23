/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signalenrichment;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

/**
 * Extension of {@code DittoCachingSignalEnrichmentFacade} that allows a selected map of selected indexes grouped by
 * namespace to be added to the signal enrichment cache.
 */
public final class SearchIndexingSignalEnrichmentFacade extends DittoCachingSignalEnrichmentFacade {

    private final Map<String, JsonFieldSelector> selectedIndexes;

    private SearchIndexingSignalEnrichmentFacade(
            final Map<String, JsonFieldSelector> selectedIndexes,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        super(cacheLoaderFacade, cacheConfig, cacheLoaderExecutor, cacheNamePrefix);

        this.selectedIndexes = Collections.unmodifiableMap(selectedIndexes);
    }

    /**
     * Returns a new {@code SearchIndexingSignalEnrichmentFacade} instance.
     *
     * @param selectedIndexes The selected indexes to be loaded into the search context
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @throws NullPointerException if any argument is null.
     */
    public static SearchIndexingSignalEnrichmentFacade newInstance(
            final Map<String, JsonFieldSelector> selectedIndexes,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        return new SearchIndexingSignalEnrichmentFacade(
                checkNotNull(selectedIndexes, "selectedIndexes"),
                checkNotNull(cacheLoaderFacade, "cacheLoaderFacade"),
                checkNotNull(cacheConfig, "cacheConfig"),
                checkNotNull(cacheLoaderExecutor, "cacheLoaderExecutor"),
                checkNotNull(cacheNamePrefix, "cacheNamePrefix"));
    }

    @Override
    protected JsonFieldSelector determineSelector(String namespace) {
        return selectedIndexes.get(namespace);
    }
}
