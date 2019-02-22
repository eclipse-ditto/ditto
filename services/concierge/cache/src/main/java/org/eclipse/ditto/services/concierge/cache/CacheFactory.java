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
package org.eclipse.ditto.services.concierge.cache;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;
import org.eclipse.ditto.services.utils.cache.config.CacheConfig;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Creates a cache configured by a {@link org.eclipse.ditto.services.utils.cache.config.CacheConfig}.
 */
@Immutable
public final class CacheFactory {

    private CacheFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a cache.
     *
     * @param cacheConfig the {@link org.eclipse.ditto.services.utils.cache.config.CacheConfig} which defines the cache's configuration.
     * @param cacheName the name of the cache. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K, V> Cache<K, V> createCache(final CacheConfig cacheConfig, final String cacheName,
            final Executor executor) {

        return CaffeineCache.of(caffeine(cacheConfig, executor), checkNotNull(cacheName, "cache name"));
    }

    /**
     * Creates a cache.
     *
     * @param cacheLoader the cache loader.
     * @param cacheConfig the the cache's configuration.
     * @param cacheName the name of the cache. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final CacheConfig cacheConfig,
            final String cacheName,
            final Executor executor) {

        checkNotNull(cacheLoader, "AsyncCacheLoader");
        checkNotNull(cacheName, "cache name");

        return CaffeineCache.of(caffeine(cacheConfig, executor), cacheLoader, cacheName);
    }


    private static Caffeine<Object, Object> caffeine(final CacheConfig cacheConfig, final Executor executor) {
        checkNotNull(cacheConfig, "CacheConfig");
        checkNotNull(executor, "Executor");

        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfig.getMaximumSize());
        caffeine.expireAfterWrite(cacheConfig.getExpireAfterWrite());
        caffeine.executor(executor);
        return caffeine;
    }

}
