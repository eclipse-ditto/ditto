/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import javax.annotation.Nullable;

import com.github.benmanes.caffeine.cache.RemovalCause;

/**
 * Listener to be called when {@link Cache} entries get invalidated (e.g. due to changes to the cached entity).
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public interface CacheInvalidationListener<K, V> {

    /**
     * Invoked when a {@link Cache} entry is invalidated.
     *
     * @param key   the invalidated key.
     * @param value the invalidated value.
     * @param removalCause the removal cause.
     */
    void onCacheEntryInvalidated(K key, @Nullable V value, RemovalCause removalCause);
}