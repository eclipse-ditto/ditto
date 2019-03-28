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
package org.eclipse.ditto.services.concierge.cache;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;

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
    public void put(final EntityId key, final Entry<EntityId> value) {
        // do nothing
    }

    @Override
    public ConcurrentMap<EntityId, Entry<EntityId>> asMap() {
        throw new UnsupportedOperationException("IdentityCache may not be viewed as map");
    }
}
