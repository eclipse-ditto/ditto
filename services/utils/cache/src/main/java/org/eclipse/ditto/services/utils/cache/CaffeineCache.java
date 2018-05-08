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
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import com.codahale.metrics.MetricRegistry;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

/**
 * A caffeine-backed cache implementation.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    private static final AsyncCacheLoader<?, ?> NULL_CACHE_LOADER =
            (k, executor) -> CompletableFuture.completedFuture(null);
    @Nullable
    private final MetricsStatsCounter metricStatsCounter;
    private AsyncLoadingCache<K, V> asyncLoadingCache;
    private LoadingCache<K, V> synchronousCacheView;


    private CaffeineCache(final Caffeine<?, ?> caffeine,
            final AsyncCacheLoader<K, V> loader,
            @Nullable final Map.Entry<String, MetricRegistry> namedMetricRegistry) {

        @SuppressWarnings("unchecked")
        final Caffeine<K, V> typedCaffeine = (Caffeine<K, V>) caffeine;
        if (namedMetricRegistry != null) {
            final String metricsPrefix = namedMetricRegistry.getKey();
            final MetricRegistry metricRegistry = namedMetricRegistry.getValue();
            this.metricStatsCounter = MetricsStatsCounter.of(metricsPrefix, metricRegistry);
            caffeine.recordStats(() -> metricStatsCounter);
            this.asyncLoadingCache = typedCaffeine.buildAsync(loader);
            this.synchronousCacheView = asyncLoadingCache.synchronous();
            metricStatsCounter.configureCache(this.synchronousCacheView);
        } else {
            this.asyncLoadingCache = typedCaffeine.buildAsync(loader);
            this.synchronousCacheView = asyncLoadingCache.synchronous();
            this.metricStatsCounter = null;
        }
    }

    /**
     * Creates a new instance based on a {@link AsyncCacheLoader}.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param asyncLoader the algorithm used for loading values asynchronously.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine, final AsyncCacheLoader<K, V> asyncLoader) {
        requireNonNull(caffeine);
        requireNonNull(asyncLoader);

        return new CaffeineCache<>(caffeine, asyncLoader, null);
    }

    /**
     * Creates a new instance based on a {@link CacheLoader}.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine, final CacheLoader<K, V> loader) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, null);
    }

    /**
     * Creates a new instance based with a Null-Cache-Loader. This is useful if the cache is populated manually.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine) {
        requireNonNull(caffeine);

        final AsyncCacheLoader<K, V> cacheLoader = getTypedNullCacheLoader();
        return new CaffeineCache<>(caffeine, cacheLoader, null);
    }

    /**
     * Creates a new instance based with a Null-Cache-Loader. This is useful if the cache is populated manually.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param namedMetricRegistry a named {@link MetricRegistry} for cache statistics, may be {@code null}.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine,
            @Nullable final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        requireNonNull(caffeine);

        final AsyncCacheLoader<K, V> cacheLoader = getTypedNullCacheLoader();
        return new CaffeineCache<>(caffeine, cacheLoader, namedMetricRegistry);
    }

    /**
     * Creates a new instance based on a {@link AsyncCacheLoader} which may report metrics for cache statistics.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values asynchronously.
     * @param namedMetricRegistry a named {@link MetricRegistry} for cache statistics, may be {@code null}.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine, final AsyncCacheLoader<K, V> loader,
            @Nullable final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, namedMetricRegistry);
    }

    /**
     * Creates a new instance based on a {@link CacheLoader} which may report metrics for cache statistics.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values.
     * @param namedMetricRegistry a named {@link MetricRegistry} for cache statistics, may be {@code null}.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<?, ?> caffeine, final CacheLoader<K, V> loader,
            @Nullable final Map.Entry<String, MetricRegistry> namedMetricRegistry) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, namedMetricRegistry);
    }

    @Override
    public CompletableFuture<Optional<V>> get(final K key) {
        requireNonNull(key);

        return asyncLoadingCache.get(key).thenApply(Optional::ofNullable);
    }

    @Override
    public Optional<V> getBlocking(final K key) {
        requireNonNull(key);

        final V value = synchronousCacheView.get(key);
        return Optional.ofNullable(value);
    }

    @Override
    public boolean invalidate(final K key) {
        requireNonNull(key);

        final boolean currentlyExisting = asyncLoadingCache.getIfPresent(key) != null;
        final boolean reportInvalidation =
                (metricStatsCounter != null) && currentlyExisting;
        synchronousCacheView.invalidate(key);

        if (reportInvalidation) {
            metricStatsCounter.recordInvalidation();
        }
        return currentlyExisting;
    }

    @Override
    public void put(final K key, final V value) {
        requireNonNull(key);
        requireNonNull(value);

        synchronousCacheView.put(key, value);
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return synchronousCacheView.asMap();
    }

    private static <K, V> AsyncCacheLoader<K, V> getTypedNullCacheLoader() {
        @SuppressWarnings("unchecked")
        final AsyncCacheLoader<K, V> nullCacheLoader = (AsyncCacheLoader<K, V>) NULL_CACHE_LOADER;
        return nullCacheLoader;
    }
}
