/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.timeseries;

import java.util.Optional;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.service.timeseries.WotLeafResolver.ResolvedLeaf;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Caches the {@code ditto:timeseries} annotation resolved for a property path of a submodel. A path whose
 * annotation cannot be parsed is cached as not ingested and counted in {@code timeseries_ingest_dropped}, so an invalid
 * model fails once per model version instead of on every event.
 */
@ThreadSafe
final class ResolvedLeafCache {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory.getThreadSafeLogger(ResolvedLeafCache.class);

    private static final Counter DROPPED = DittoMetrics.counter("timeseries_ingest_dropped");

    private final Cache<LeafKey, CachedLeaf> cache;

    ResolvedLeafCache(final long maximumSize) {
        cache = Caffeine.newBuilder().maximumSize(maximumSize).build();
    }

    /**
     * The resolved annotation only depends on the submodel and the path. The WoT resolver hands out the same
     * {@link ThingModel} instance while it is cached, so a different instance means the model was reloaded.
     */
    Optional<ResolvedLeaf> resolve(final ThingSubmodel submodel, final ThingModel submodelTm,
            final JsonPointer path) {

        final LeafKey key = new LeafKey(submodel.href(), path);
        final CachedLeaf cached = cache.getIfPresent(key);
        if (cached != null && cached.model() == submodelTm) {
            return cached.leaf();
        }
        return cache.asMap().compute(key, (k, current) -> current != null && current.model() == submodelTm
                ? current
                : new CachedLeaf(submodelTm, resolveOrNotIngested(submodel.href(), submodelTm, path))).leaf();
    }

    private static Optional<ResolvedLeaf> resolveOrNotIngested(final IRI submodel, final ThingModel submodelTm,
            final JsonPointer path) {

        try {
            return WotLeafResolver.resolveLeaf(submodelTm, path);
        } catch (final RuntimeException e) {
            DROPPED.increment();
            LOGGER.warn("Not ingesting property <{}> of model <{}>: invalid ditto:timeseries annotation: {}",
                    path, submodel, e.getMessage());
            return Optional.empty();
        }
    }

    private record LeafKey(IRI submodel, JsonPointer path) {}

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private record CachedLeaf(ThingModel model, Optional<ResolvedLeaf> leaf) {}
}
