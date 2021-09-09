/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cache;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Policy;


/**
 * A caffeine-backed cache implementation.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public class CaffeineCache<K, V> implements Cache<K, V> {

    @Nullable private final MetricsStatsCounter metricStatsCounter;
    private final BiFunction<K, Executor, CompletableFuture<? extends V>> asyncLoad;
    private final AsyncCache<K, V> asyncCache;
    private final com.github.benmanes.caffeine.cache.Cache<K, V> synchronousCacheView;


    private CaffeineCache(final Caffeine<? super K, ? super V> caffeine,
            @Nullable final AsyncCacheLoader<K, V> loader,
            @Nullable final String cacheName) {

        if (cacheName != null) {
            this.metricStatsCounter =
                    MetricsStatsCounter.of(cacheName, this::getMaxCacheSize, this::getCurrentCacheSize);
            caffeine.recordStats(() -> metricStatsCounter);
        } else {
            this.metricStatsCounter = null;
        }
        asyncLoad = getLoaderFunction(loader);
        this.asyncCache = loader != null ? caffeine.buildAsync(loader) : caffeine.buildAsync();
        this.synchronousCacheView = asyncCache.synchronous();
    }

    private BiFunction<K, Executor, CompletableFuture<? extends V>> getLoaderFunction(
            @Nullable final AsyncCacheLoader<K, V> loader) {
        return loader != null ?
                (k, e) -> {
                    try {
                        return loader.asyncLoad(k, e);
                    } catch (final Exception ex) {
                        return CompletableFuture.completedFuture(null);
                    }
                } :
                (k, e) -> CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings({"squid:S2583", "ConstantConditions"})
    private Long getCurrentCacheSize() {
        if (synchronousCacheView == null) {
            // This can occur if this method is called by metricStatsCounter before the cache has been initialized.
            return 0L;
        }

        return synchronousCacheView.estimatedSize();
    }

    @SuppressWarnings({"squid:S2583", "ConstantConditions"})
    private Long getMaxCacheSize() {
        if (synchronousCacheView == null) {
            // This can occur if this method is called by metricStatsCounter before the cache has been initialized.
            return 0L;
        }

        return synchronousCacheView.policy().eviction().map(Policy.Eviction::getMaximum).orElse(0L);
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
     * Creates a new instance based with a Null-Cache-Loader. This is useful if the cache is populated manually.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param <K> the type of the key.
     * @param <V> the type of the value.
     * @return the created instance
     */
    public static <K, V> CaffeineCache<K, V> of(final Caffeine<? super K, ? super V> caffeine) {
        requireNonNull(caffeine);

        return new CaffeineCache<>(caffeine, null, null);
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

        return new CaffeineCache<>(caffeine, null, cacheName);
    }

    /**
     * Creates a new instance based on a {@link AsyncCacheLoader} which may report metrics for cache statistics.
     *
     * @param caffeine a (pre-configured) caffeine instance.
     * @param loader the algorithm used for loading values asynchronously.
     * @param cacheName The name of the cache or {@code null} if metrics should be disabled. Will be used for metrics.
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

    @Override
    public CompletableFuture<Optional<V>> get(final K key) {
        requireNonNull(key);

        return asyncCache.get(key, asyncLoad).thenApply(Optional::ofNullable);
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

        return asyncCache.get(key, mappingFunction);
    }

    @Override
    public CompletableFuture<Optional<V>> getIfPresent(final K key) {
        requireNonNull(key);

        final CompletableFuture<V> future = asyncCache.getIfPresent(key);

        return future == null
                ? CompletableFuture.completedFuture(Optional.empty())
                : future.thenApply(Optional::ofNullable);
    }

    @Override
    public Optional<V> getBlocking(final K key) {
        requireNonNull(key);

        return get(key).join();
    }

    @Override
    public boolean invalidate(final K key) {
        requireNonNull(key);

        final boolean currentlyExisting = asyncCache.getIfPresent(key) != null;
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
        asyncCache.put(key, CompletableFuture.completedFuture(value));
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return synchronousCacheView.asMap();
    }

}
