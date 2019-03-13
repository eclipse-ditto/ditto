/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.cache;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

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


    private CaffeineCache(final Caffeine<? super K, ? super V> caffeine,
            final AsyncCacheLoader<K, V> loader,
            @Nullable final String cacheName) {

        if (cacheName != null) {
            this.metricStatsCounter = MetricsStatsCounter.of(cacheName);
            caffeine.recordStats(() -> metricStatsCounter);
            this.asyncLoadingCache = caffeine.buildAsync(loader);
            this.synchronousCacheView = asyncLoadingCache.synchronous();
            metricStatsCounter.configureCache(this.synchronousCacheView);
        } else {
            this.asyncLoadingCache = caffeine.buildAsync(loader);
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
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine,
            final AsyncCacheLoader<K, V> asyncLoader) {
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
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine,
            final CacheLoader<K, V> loader) {
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
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine) {
        requireNonNull(caffeine);

        final AsyncCacheLoader<K, V> cacheLoader = getTypedNullCacheLoader();
        return new CaffeineCache<>(caffeine, cacheLoader, null);
    }

    /**
     * Creates a new instance based with a Null-Cache-Loader. This is useful if the cache is populated manually.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param cacheName The name of the cache {@code null}. Will be used for metrics.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine,
            @Nullable final String cacheName) {
        requireNonNull(caffeine);

        final AsyncCacheLoader<K, V> cacheLoader = getTypedNullCacheLoader();
        return new CaffeineCache<>(caffeine, cacheLoader, cacheName);
    }

    /**
     * Creates a new instance based on a {@link AsyncCacheLoader} which may report metrics for cache statistics.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values asynchronously.
     * @param cacheName The name of the cache {@code null}. Will be used for metrics.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine,
            final AsyncCacheLoader<K, V> loader,
            @Nullable final String cacheName) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, cacheName);
    }

    /**
     * Creates a new instance based on a {@link CacheLoader} which may report metrics for cache statistics.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values.
     * @param cacheName The name of the cache {@code null}. Will be used for metrics.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine,
            final CacheLoader<K, V> loader,
            @Nullable final String cacheName) {
        requireNonNull(caffeine);
        requireNonNull(loader);

        return new CaffeineCache<>(caffeine, loader, cacheName);
    }

    @Override
    public CompletableFuture<Optional<V>> get(final K key) {
        requireNonNull(key);

        return asyncLoadingCache.get(key).thenApply(Optional::ofNullable);
    }

    /**
     * Lookup a value in cache, or create it via {@code mappingFunction} and store it if the value was not cached.
     * Only available for Caffeine caches.
     *
     * @param key key associated with the value in cache.
     * @param mappingFunction function to create the value in case of absence.
     * @return future value under normal circumstances, or a failed future if the mapping function fails.
     */
    public CompletableFuture<V> get(final K key,
            final BiFunction<K, Executor, CompletableFuture<V>> mappingFunction) {

        return asyncLoadingCache.get(key, mappingFunction);
    }

    @Override
    public CompletableFuture<Optional<V>> getIfPresent(final K key) {
        requireNonNull(key);

        final CompletableFuture<V> future = asyncLoadingCache.getIfPresent(key);

        return future == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : future.thenApply(Optional::ofNullable);
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
        synchronousCacheView.invalidate(key);

        if (metricStatsCounter != null) {
            if (currentlyExisting) {
                metricStatsCounter.recordInvalidation();
            } else {
                metricStatsCounter.recordInvalidationWithoutItem();
            }
        }
        return currentlyExisting;
    }

    // optimized batch invalidation method for caffeine
    @Override
    public void invalidateAll(final Collection<K> keys) {
        synchronousCacheView.invalidateAll(keys);
    }

    @Override
    public void put(final K key, final V value) {
        requireNonNull(key);
        requireNonNull(value);

        // non-blocking.
        // synchronousCacheView.put has same implementation with extra null check on value.
        asyncLoadingCache.put(key, CompletableFuture.completedFuture(value));
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return synchronousCacheView.asMap();
    }

    // TODO: replace uses of this method by AsyncCache without loader once Caffeine releases it.
    private static <K, V> AsyncCacheLoader<K, V> getTypedNullCacheLoader() {
        @SuppressWarnings("unchecked") final AsyncCacheLoader<K, V> nullCacheLoader =
                (AsyncCacheLoader<K, V>) NULL_CACHE_LOADER;
        return nullCacheLoader;
    }
}
