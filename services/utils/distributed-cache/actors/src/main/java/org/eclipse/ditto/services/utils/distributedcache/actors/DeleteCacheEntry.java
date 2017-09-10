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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Command for deleting a {@link CacheEntry} identified by a {@code id}.
 */
@Immutable
public final class DeleteCacheEntry implements CacheModifyCommand {

    private final String id;
    private final CacheEntry cacheEntryToDelete;
    private final long revisionNumber;
    private final WriteConsistency writeConsistency;

    /**
     * Constructs a new {@code DeleteCacheEntry} object.
     *
     * @param id the ID to delete from the Cache.
     * @param cacheEntryToDelete the cache entry to delete.
     * @param revisionNumber the revision number of the event which caused to delete the cache entry.
     * @param writeConsistency the write consistency.
     * @throws NullPointerException if {@code id} or {@code writeConsistency} is {@code null}.
     */
    public DeleteCacheEntry(final String id, final CacheEntry cacheEntryToDelete,
            final long revisionNumber, final WriteConsistency writeConsistency) {
        this.id = checkNotNull(id, "ID to delete from the cache");
        this.cacheEntryToDelete = cacheEntryToDelete;
        this.revisionNumber = revisionNumber;
        this.writeConsistency = checkNotNull(writeConsistency, "write consistency");
    }

    /**
     * Returns the revision number of the event which caused to delete the cache entry.
     *
     * @return the revision number.
     */
    public long getRevisionNumber() {
        return revisionNumber;
    }

    /**
     * Returns the state of the cache entry after deletion.
     *
     * @return The cache entry after deletion.
     */
    public CacheEntry getDeletedCacheEntry() {
        return cacheEntryToDelete.asDeleted(revisionNumber);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public WriteConsistency getWriteConsistency() {
        return writeConsistency;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteCacheEntry that = (DeleteCacheEntry) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(cacheEntryToDelete, that.cacheEntryToDelete) &&
                revisionNumber == that.revisionNumber &&
                writeConsistency == that.writeConsistency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cacheEntryToDelete, revisionNumber, writeConsistency);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", revisionNumber=" + revisionNumber +
                ", writeConsistency=" + writeConsistency +
                "]";
    }

}
