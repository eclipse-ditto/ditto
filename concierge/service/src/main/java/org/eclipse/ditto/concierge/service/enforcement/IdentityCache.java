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
package org.eclipse.ditto.concierge.service.enforcement;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.CacheInvalidationListener;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;

/**
 * Cache that returns the key as result.
 */
final class IdentityCache implements Cache<EnforcementCacheKey, Entry<EnforcementCacheKey>> {

    /**
     * The single instance of this cache.
     */
    static final IdentityCache INSTANCE = new IdentityCache();

    private IdentityCache() {

    }

    @Override
    public CompletableFuture<Optional<Entry<EnforcementCacheKey>>> get(final EnforcementCacheKey key) {
        return CompletableFuture.completedFuture(getBlocking(key));
    }

    @Override
    public CompletableFuture<Optional<Entry<EnforcementCacheKey>>> getIfPresent(final EnforcementCacheKey key) {
        return get(key);
    }

    @Override
    public Optional<Entry<EnforcementCacheKey>> getBlocking(final EnforcementCacheKey key) {
        return Optional.of(Entry.permanent(key));
    }

    @Override
    public boolean invalidate(final EnforcementCacheKey key) {
        // do nothing
        return false;
    }

    @Override
    public void subscribeForInvalidation(
            final CacheInvalidationListener<EnforcementCacheKey, Entry<EnforcementCacheKey>> invalidationListener) {
        // do nothing
    }

    @Override
    public void put(final EnforcementCacheKey key, final Entry<EnforcementCacheKey> value) {
        // do nothing
    }

    @Override
    public ConcurrentMap<EnforcementCacheKey, Entry<EnforcementCacheKey>> asMap() {
        throw new UnsupportedOperationException("IdentityCache may not be viewed as map");
    }

}
