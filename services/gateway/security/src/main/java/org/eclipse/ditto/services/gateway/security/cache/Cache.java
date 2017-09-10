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
package org.eclipse.ditto.services.gateway.security.cache;

import java.util.Optional;

/**
 * A general purpose cache for items which are associated with a key.
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public interface Cache<K, V> {

    /**
     * Returns the value which is associated with the specified key.
     *
     * @param key the key to get the associated value for.
     * @return the value which is associated with the specified key.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    Optional<V> get(K key);

    /**
     * Associates the specified value with the specified key.
     *
     * @param key the key to be associated with {@code value}.
     * @param value the value to be associated with {@code key}.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    void put(K key, V value);

    /**
     * Removes the passed key from the cache if present.
     *
     * @param key the key to remove from cache.
     * @return {@code true} if the key was present and deleted from cache, {@code false} otherwise.
     */
    boolean remove(K key);

}
