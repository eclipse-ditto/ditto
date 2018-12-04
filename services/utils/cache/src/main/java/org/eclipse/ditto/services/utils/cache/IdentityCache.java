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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.ditto.services.models.caching.EntityId;
import org.eclipse.ditto.services.models.caching.Entry;

/**
 * Cache that returns the key as result.
 */
public final class IdentityCache implements Cache<EntityId, Entry<EntityId>> {

    /**
     * The single instance of this cache.
     */
    public static final IdentityCache INSTANCE = new IdentityCache();

    private IdentityCache() {

    }

    @Override
    public CompletableFuture<Optional<Entry<EntityId>>> get(final EntityId key) {
        return CompletableFuture.completedFuture(getBlocking(key));
    }

    @Override
    public CompletableFuture<Optional<Entry<EntityId>>> getIfPresent(final EntityId key) {
        return get(key);
    }

    @Override
    public Optional<Entry<EntityId>> getBlocking(final EntityId key) {
        return Optional.of(Entry.permanent(key));
    }

    @Override
    public boolean invalidate(final EntityId key) {
        // do nothing
        return false;
    }

    @Override
    public void subscribeForInvalidation(
            final CacheInvalidationListener<EntityId, Entry<EntityId>> invalidationListener) {
        // do nothing
    }

    @Override
    public void put(final EntityId key, final Entry<EntityId> value) {
        // do nothing
    }

    @Override
    public ConcurrentMap<EntityId, Entry<EntityId>> asMap() {
        throw new UnsupportedOperationException("IdentityCache may not be viewed as map");
    }
}
