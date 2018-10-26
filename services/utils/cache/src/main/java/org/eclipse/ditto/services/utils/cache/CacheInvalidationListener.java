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
package org.eclipse.ditto.services.utils.cache;

import javax.annotation.Nullable;

/**
 * Listener to be called when {@link Cache} entries get invalidated explicitly (e.g. due to changes to the cached
 * entity).
 *
 * @param <K> the type of the key.
 * @param <V> the type of the value.
 */
public interface CacheInvalidationListener<K, V> {

    /**
     * Invoked when a {@link Cache} entry is invalidated explicitly.
     *
     * @param key the invalidated key.
     * @param value the nullable invalidated value.
     */
    void onCacheEntryInvalidated(K key, @Nullable V value);
}
