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
        caffeine.expireAfterAccess(cacheConfigReader.expireAfterAccess());
        caffeine.executor(executor);
        return caffeine;
    }

}
