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
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.utils.distributedcache.model.CacheEntry;

/**
 * Command for retrieving a {@link CacheEntry} identified by a {@code id}.
 */
@NotThreadSafe
public final class RetrieveCacheEntry implements CacheCommand, WithContext {

    private final String id;
    @Nullable private final Object context;
    private final ReadConsistency readConsistency;

    /**
     * Creates a new {@code RetrieveCacheEntry}.
     *
     * @param id the ID to retrieve from the Cache.
     * @param context the arbitrary context which can be used for correlation.
     * @param readConsistency the ReadConsistency of how to lookup the Cache entry.
     * @throws NullPointerException if {@code id} or {@code readConsistency} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public RetrieveCacheEntry(final String id, @Nullable final Object context, final ReadConsistency readConsistency) {
        this.id = argumentNotEmpty(id, "ID");
        this.context = context;
        this.readConsistency = checkNotNull(readConsistency, "read consistency");
    }

    /**
     * Returns the {@code ReadConsistency} of how to lookup the Cache entry.
     *
     * @return the ReadConsistency.
     */
    public ReadConsistency getReadConsistency() {
        return readConsistency;
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
        final RetrieveCacheEntry that = (RetrieveCacheEntry) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(context, that.context) &&
                Objects.equals(readConsistency, that.readConsistency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, context, readConsistency);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "id=" + id +
                ", context=" + context +
                ", readConsistency=" + readConsistency +
                "]";
    }

}
