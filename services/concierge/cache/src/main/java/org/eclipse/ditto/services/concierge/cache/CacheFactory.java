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

import org.eclipse.ditto.services.concierge.util.config.CacheConfigReader;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.services.utils.cache.CaffeineCache;

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
     *
     * @param cacheLoader the cache loader.
     * @param cacheConfigReader the {@link CacheConfigReader} which defines the cache's configuration.
     * @param cacheName the name of the cache. Used as metric label.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final CacheConfigReader cacheConfigReader,
            final String cacheName) {
        requireNonNull(cacheLoader);
        requireNonNull(cacheConfigReader);
        requireNonNull(cacheName);

        return CaffeineCache.of(caffeine(cacheConfigReader), cacheLoader, cacheName);
    }

    private static Caffeine<Object, Object> caffeine(final CacheConfigReader cacheConfigReader) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfigReader.maximumSize());
        caffeine.expireAfterWrite(cacheConfigReader.expireAfterWrite());
        return caffeine;
    }
}
