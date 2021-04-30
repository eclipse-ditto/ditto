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
package org.eclipse.ditto.internal.utils.cacheloaders;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheKey;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;

/**
 * Cache that returns the key as result.
 */
public final class IdentityCache implements Cache<CacheKey, Entry<CacheKey>> {

    /**
     * The single instance of this cache.
     */
    public static final IdentityCache INSTANCE = new IdentityCache();

    private IdentityCache() {

    }

    @Override
    public CompletableFuture<Optional<Entry<CacheKey>>> get(final CacheKey key) {
        return CompletableFuture.completedFuture(getBlocking(key));
    }

    @Override
    public CompletableFuture<Optional<Entry<CacheKey>>> getIfPresent(final CacheKey key) {
        return get(key);
    }

    @Override
    public Optional<Entry<CacheKey>> getBlocking(final CacheKey key) {
        return Optional.of(Entry.permanent(key));
    }

    @Override
    public boolean invalidate(final CacheKey key) {
        // do nothing
        return false;
    }

    @Override
    public void put(final CacheKey key, final Entry<CacheKey> value) {
        // do nothing
    }

    @Override
    public ConcurrentMap<CacheKey, Entry<CacheKey>> asMap() {
        throw new UnsupportedOperationException("IdentityCache may not be viewed as map");
    }
}
