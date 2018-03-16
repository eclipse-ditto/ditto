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
package org.eclipse.ditto.services.utils.cache;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * A caffeine-backed cache implementation.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private AsyncLoadingCache<K, V> asyncLoadingCache;
    private LoadingCache<K, V> synchronousCacheView;

    private CaffeineCache(final Caffeine<K, V> caffeine, final AsyncCacheLoader<K, V> loader,
            @Nullable final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        if (namedMetricRegistry != null) {
            caffeine.recordStats(() -> {
                final String metricsPrefix = namedMetricRegistry.getKey();
                final MetricRegistry metricRegistry = namedMetricRegistry.getValue();
                return MetricsStatsCounter.of(metricsPrefix, metricRegistry);
            });
        }

        this.asyncLoadingCache = caffeine.buildAsync(loader);
        synchronousCacheView = asyncLoadingCache.synchronous();
    }

    /**
     * Creates a new instance.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<K, V> caffeine, final AsyncCacheLoader<K, V> loader) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, null);
    }

    /**
     * Creates a new instance.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values.
     * @param namedMetricRegistry a named {@link MetricRegistry} for cache statistics.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<K, V> caffeine, final AsyncCacheLoader<K, V> loader,
            final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        requireNonNull(caffeine);
        requireNonNull(loader);
        requireNonNull(namedMetricRegistry);

        return new CaffeineCache<>(caffeine, loader, namedMetricRegistry);
    }

    @Override
    public CompletableFuture<Optional<V>> get(final K key) {
        return asyncLoadingCache.get(key).thenApply(Optional::ofNullable);
    }

    @Override
    public Optional<V> getBlocking(final K key) {
        final V value = synchronousCacheView.get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public void invalidate(final K key) {
        synchronousCacheView.invalidate(key);
    }

}
