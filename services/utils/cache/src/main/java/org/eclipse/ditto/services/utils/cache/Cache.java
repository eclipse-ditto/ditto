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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

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
     * Prefer using a cache-loader instead.
     *</p>
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
}
