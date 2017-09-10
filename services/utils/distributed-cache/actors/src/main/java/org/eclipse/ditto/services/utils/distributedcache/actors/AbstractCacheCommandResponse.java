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

import javax.annotation.concurrent.Immutable;

/**
 * Abstract implementation of {@link CacheCommandResponse} to reduce implementation redundancy.
 */
@Immutable
abstract class AbstractCacheCommandResponse implements CacheCommandResponse {

    private final String id;
    private final boolean success;

    /**
     * Constructs a new {@code AbstractCacheCommandResponse} object.
     *
     * @param id the ID of the deleted cache entry.
     * @param success whether deleting a cache entry was a success or not.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    protected AbstractCacheCommandResponse(final CharSequence id, final boolean success) {
        this.id = argumentNotEmpty(id, "ID").toString();
        this.success = success;
    }

    /**
     * Indicates whether deleting a CacheEntry was successful.
     *
     * @return {@code true} if the CacheEntry was deleted, else {@code false}.
     */
    public boolean isSuccess() {
        return success;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractCacheCommandResponse that = (AbstractCacheCommandResponse) o;
        return success == that.success && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, success);
    }

    @Override
    public String toString() {
        return "id=" + id + ", success=" + success;
    }

}
