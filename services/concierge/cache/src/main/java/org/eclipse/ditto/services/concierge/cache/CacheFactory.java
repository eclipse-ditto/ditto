/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.cache;

import static java.util.Objects.requireNonNull;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.ditto.services.concierge.util.config.CacheConfigReader;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Creates a cache configured by a {@link CacheConfigReader}.
 */
public final class CacheFactory {

    private CacheFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a cache.
     * @param cacheLoader the cache loader.
     * @param cacheConfigReader the {@link CacheConfigReader} which defines the cache's configuration.
     * @param metricName the name of the metric provided for the cache.
     * @param metricsReportingConsumer a consumer which configures the cache for metrics reporting.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final CacheConfigReader cacheConfigReader,
            final String metricName,
            final Consumer<AbstractMap.Entry<String, MetricRegistry>> metricsReportingConsumer) {
        requireNonNull(cacheLoader);
        requireNonNull(cacheConfigReader);
        requireNonNull(metricName);
        requireNonNull(metricsReportingConsumer);

        final AbstractMap.SimpleEntry<String, MetricRegistry> namedEnforcerMetricRegistry =
                new AbstractMap.SimpleEntry<>(metricName, new MetricRegistry());
        final Cache<K, V>  cache = createCache(cacheConfigReader, cacheLoader,
                namedEnforcerMetricRegistry);
        metricsReportingConsumer.accept(namedEnforcerMetricRegistry);

        return cache;
    }

    private static <K, V> CaffeineCache<K, V> createCache(final CacheConfigReader cacheConfigReader,
            final AsyncCacheLoader<K, V> loader, Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        return CaffeineCache.of(caffeine(cacheConfigReader), loader, namedMetricRegistry);
    }

    private static Caffeine<Object, Object> caffeine(final CacheConfigReader cacheConfigReader) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfigReader.maximumSize());
        caffeine.expireAfterWrite(cacheConfigReader.expireAfterWrite());
        return caffeine;
    }
}
