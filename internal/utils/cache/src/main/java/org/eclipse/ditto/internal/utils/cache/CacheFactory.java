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
package org.eclipse.ditto.internal.utils.cache;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

/**
 * Creates a cache configured by a {@link org.eclipse.ditto.internal.utils.cache.config.CacheConfig}.
 */
@Immutable
public final class CacheFactory {

    private CacheFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a cache.
     *
     * @param cacheConfig the {@link org.eclipse.ditto.internal.utils.cache.config.CacheConfig} which defines the cache's configuration.
     * @param cacheName the name of the cache or {@code null} if metrics should be disabled. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K, V> Cache<K, V> createCache(final CacheConfig cacheConfig, @Nullable final String cacheName,
            final Executor executor) {

        return CaffeineCache.of(caffeine(cacheConfig, executor), cacheName);
    }

    /**
     * Creates a cache.
     *
     * @param cacheLoader the cache loader.
     * @param cacheConfig the cache's configuration.
     * @param cacheName the name of the cache or {@code null} if metrics should be disabled. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final CacheConfig cacheConfig,
            @Nullable final String cacheName,
            final Executor executor) {

        checkNotNull(cacheLoader, "AsyncCacheLoader");

        return CaffeineCache.of(caffeine(cacheConfig, executor), cacheLoader, cacheName);
    }

    /**
     * Creates a cache with a custom provided expiry policy.
     *
     * @param cacheLoader the cache loader.
     * @param expiry a custom expiry policy, that e.g. reads the expiration time from the cache key or value
     * @param cacheConfig the cache's configuration (note: expiration times are ignored if expiry is given)
     * @param cacheName the name of the cache or {@code null} if metrics should be disabled. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final Expiry<K, V> expiry,
            final CacheConfig cacheConfig,
            @Nullable final String cacheName,
            final Executor executor) {
        checkNotNull(cacheLoader, "cacheLoader");
        checkNotNull(expiry, "expiry");
        return CaffeineCache.of(caffeine(cacheConfig, executor, expiry), cacheLoader, cacheName);
    }

    private static Caffeine<Object, Object> caffeine(final CacheConfig cacheConfig, final Executor executor) {
        checkNotNull(cacheConfig, "CacheConfig");
        checkNotNull(executor, "Executor");

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfig.getMaximumSize());

        if (!cacheConfig.getExpireAfterCreate().isZero()) {
            // special case "expire-after-create" needs the following API invocation of Caffeine:
            caffeine.expireAfter(new Expiry<Object, Object>() {
                @Override
                public long expireAfterCreate(final Object key, final Object value, final long currentTime) {
                    return cacheConfig.getExpireAfterCreate().toNanos();
                }

                @Override
                public long expireAfterUpdate(final Object key, final Object value, final long currentTime,
                        final long currentDuration) {
                    return currentDuration;
                }

                @Override
                public long expireAfterRead(final Object key, final Object value, final long currentTime,
                        final long currentDuration) {
                    return currentDuration;
                }
            });
        } else {
            caffeine.expireAfterWrite(cacheConfig.getExpireAfterWrite());
            caffeine.expireAfterAccess(cacheConfig.getExpireAfterAccess());
        }
        caffeine.executor(executor);
        return caffeine;
    }

    private static Caffeine<Object, Object> caffeine(final CacheConfig cacheConfig, final Executor executor,
            final Expiry<?, ?> expiry) {
        checkNotNull(cacheConfig, "CacheConfig");
        checkNotNull(executor, "Executor");
        checkNotNull(expiry, "expiry");

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfig.getMaximumSize());
        caffeine.expireAfter(expiry);
        caffeine.executor(executor);
        return caffeine;
    }

}
