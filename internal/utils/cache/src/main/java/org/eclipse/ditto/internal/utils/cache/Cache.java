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

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * A general purpose cache for items which are associated with a key.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public interface Cache<K, V> {

    /**
     * Returns a {@link CompletableFuture} returning the value which is associated with the specified key.
     *
     * @param key the key to get the associated value for.
     * @return a {@link CompletableFuture} returning the value which is associated with the specified key or an empty
     * {@link Optional}.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    CompletableFuture<Optional<V>> get(K key);

    /**
     * Retrieve the value associated with a key in a future if it exists in the cache, or a future empty optional if
     * it does not. The cache loader will never be called.
     *
     * @param key the key.
     * @return the optional associated value in a future.
     */
    CompletableFuture<Optional<V>> getIfPresent(K key);

    /**
     * Returns the value which is associated with the specified key.
     *
     * @param key the key to get the associated value for.
     * @return the value which is associated with the specified key or an empty
     * {@link Optional}.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    Optional<V> getBlocking(K key);

    /**
     * Invalidates the passed key from the cache if present.
     *
     * @param key the key to invalidate.
     * @return {@code true} if the entry was cached and is now invalidated, {@code false} otherwise.
     */
    boolean invalidate(K key);

    /**
     * Associates the {@code value} with the {@code key} in this cache.
     * <p>
     * Prefer using a cache-loader instead. The current thread will not wait for cache update to complete.
     * </p>
     *
     * @param key the key.
     * @param value the value.
     * @throws NullPointerException if either the given {@code key} or {@code value} is null.
     */
    void put(final K key, final V value);

    /**
     * Returns a synchronous view of the entries stored in this cache as a (thread-safe) map.
     * Modifications directly affect the cache.
     *
     * @return a view of this cache
     * @see com.github.benmanes.caffeine.cache.Cache
     */
    ConcurrentMap<K, V> asMap();

    /**
     * Invalidate a collection of keys.
     *
     * @param keys all keys to invalidate.
     */
    default void invalidateAll(final Collection<K> keys) {
        keys.forEach(this::invalidate);
    }

    /**
     * Builds a cache containing projected values of provided type {@code <U>} on this cache using the passed in
     * functions in order to transform between the value of type {@code <V>} this cache instance manages and the value
     * of type {@code <U>} the returned cache projection holds.
     *
     * @param project the projection of a value {@code this} cache manages to a value of the returned cache
     * @param embed the function of a value added to the returned cache to a value of {@code this} cache
     * @param <U> the type of the projected cache's values
     * @return the cache with projected values.
     */
    default <U> Cache<K, U> projectValues(final Function<V, U> project, final Function<U, V> embed) {
        return new ProjectedCache<>(this, project, embed);
    }
}
