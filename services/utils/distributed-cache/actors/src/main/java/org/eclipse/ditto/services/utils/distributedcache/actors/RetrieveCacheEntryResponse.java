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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Response to {@link RetrieveCacheEntry} containing a {@link CacheEntry} for a asked {@code id}.
 */
@NotThreadSafe
public final class RetrieveCacheEntryResponse implements CacheCommandResponse, WithContext {

    private final String id;
    @Nullable private final CacheEntry cacheEntry;
    @Nullable private final Object context;

    /**
     * Constructs a new {@code RetrieveCacheEntryResponse} object.
     *
     * @param id the retrieved ID from the Cache.
     * @param cacheEntry the looked up {@link CacheEntry}.
     * @param context the arbitrary context which can be used for correlation.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public RetrieveCacheEntryResponse(final String id, @Nullable final CacheEntry cacheEntry,
            @Nullable final Object context) {

        this.id = argumentNotEmpty(id, "ID");
        this.cacheEntry = cacheEntry;
        this.context = context;
    }

    /**
     * Returns the looked up CacheEntry if it was found.
     *
     * @return the CacheEntry or an empty Optional.
     */
    public Optional<CacheEntry> getCacheEntry() {
        return Optional.ofNullable(cacheEntry);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Optional<Object> getContext() {
        return Optional.ofNullable(context);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveCacheEntryResponse that = (RetrieveCacheEntryResponse) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(cacheEntry, that.cacheEntry) &&
                Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cacheEntry, context);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", cacheEntry=" + cacheEntry +
                ", context=" + context +
                "]";
    }

}
