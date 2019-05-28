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
package org.eclipse.ditto.services.utils.cache;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Executor;

import org.eclipse.ditto.services.utils.cache.config.CacheConfigReader;

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
     * Create a new entity ID from the given  {@code resourceType} and {@code id}.
     *
     * @param resourceType the resource type.
     * @param id the entity ID.
     * @return the entity ID with resource type object.
     */
    public static EntityId newEntityId(final String resourceType, final String id) {
        return ImmutableEntityId.of(resourceType, id);
    }

    /**
     * Deserialize entity ID with resource type from a string.
     *
     * @param string the string.
     * @return the entity ID with resource type.
     * @throws IllegalArgumentException if the string does not have the expected format.
     */
    public static EntityId readEntityIdFrom(final String string) {
        return ImmutableEntityId.readFrom(string);
    }

    /**
     * Creates a cache.
     *
     * @param cacheConfigReader the {@link CacheConfigReader} which defines the cache's configuration.
     * @param cacheName the name of the cache. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     */
    public static <K, V> Cache<K, V> createCache(final CacheConfigReader cacheConfigReader,
            final String cacheName,
            final Executor executor) {
        requireNonNull(cacheConfigReader);
        requireNonNull(cacheName);

        return CaffeineCache.of(caffeine(cacheConfigReader, executor), cacheName);
    }

    /**
     * Creates a cache.
     *
     * @param cacheLoader the cache loader.
     * @param cacheConfigReader the {@link CacheConfigReader} which defines the cache's configuration.
     * @param cacheName the name of the cache. Used as metric label.
     * @param executor the executor to use in the cache.
     * @param <K> the type of the cache keys.
     * @param <V> the type of the cache values.
     * @return the created cache.
     */
    public static <K, V> Cache<K, V> createCache(final AsyncCacheLoader<K, V> cacheLoader,
            final CacheConfigReader cacheConfigReader,
            final String cacheName,
            final Executor executor) {
        requireNonNull(cacheLoader);
        requireNonNull(cacheConfigReader);
        requireNonNull(cacheName);

        return CaffeineCache.of(caffeine(cacheConfigReader, executor), cacheLoader, cacheName);
    }


    private static Caffeine<Object, Object> caffeine(final CacheConfigReader cacheConfigReader,
            final Executor executor) {
        final Caffeine<Object, Object> caffeine = Caffeine.newBuilder();
        caffeine.maximumSize(cacheConfigReader.maximumSize());
        caffeine.expireAfterWrite(cacheConfigReader.expireAfterWrite());
        caffeine.expireAfterAccess(cacheConfigReader.expireAfterAccess());
        caffeine.executor(executor);
        return caffeine;
    }

}
