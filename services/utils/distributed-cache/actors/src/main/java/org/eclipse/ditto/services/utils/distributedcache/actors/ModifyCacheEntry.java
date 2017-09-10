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
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Command for modifying a {@link CacheEntry} identified by an {@code id}.
 */
@Immutable
public final class ModifyCacheEntry implements CacheModifyCommand {

    private final String id;
    private final CacheEntry cacheEntry;
    private final WriteConsistency writeConsistency;

    /**
     * Constructs a new {@code ModifyCacheEntry} object.
     *
     * @param id the ID of the entity to be modified in the Cache.
     * @param cacheEntry the modified CacheEntry.
     * @param writeConsistency the write consistency.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public ModifyCacheEntry(final String id, final CacheEntry cacheEntry, final WriteConsistency writeConsistency) {
        this.id = argumentNotEmpty(id, "ID");
        this.cacheEntry = checkNotNull(cacheEntry, "modified cache entry");
        this.writeConsistency = checkNotNull(writeConsistency, "write consistency");
    }

    /**
     * Returns the {@code CacheEntry} to be modified.
     *
     * @return the CacheEntry.
     */
    public CacheEntry getCacheEntry() {
        return cacheEntry;
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
        final ModifyCacheEntry that = (ModifyCacheEntry) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(cacheEntry, that.cacheEntry) &&
                writeConsistency == that.writeConsistency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cacheEntry, writeConsistency);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", id=" + id +
                ", cacheEntry=" + cacheEntry +
                ", writeConsistency=" + writeConsistency +
                "]";
    }

}
