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

import javax.annotation.concurrent.Immutable;

/**
 * Response to {@link DeleteCacheEntry}.
 */
@Immutable
public final class DeleteCacheEntryResponse extends AbstractCacheCommandResponse {

    private DeleteCacheEntryResponse(final CharSequence id, final boolean success) {
        super(id, success);
    }

    /**
     * Returns a new instance of {@code DeleteCacheEntryResponse} which represents a successful deletion of the cache
     * entry with the specified ID.
     *
     * @param id the ID of the deleted cache entry.
     * @return the instance.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public static DeleteCacheEntryResponse forSucceeded(final CharSequence id) {
        return new DeleteCacheEntryResponse(id, true);
    }

    /**
     * Returns a new instance of {@code DeleteCacheEntryResponse} which represents a failed deletion of the cache
     * entry with the specified ID.
     *
     * @param id the ID of the deleted cache entry.
     * @return the instance.
     * @throws NullPointerException if {@code id} is {@code null}.
     * @throws IllegalArgumentException if {@code id} is empty.
     */
    public static DeleteCacheEntryResponse forFailed(final CharSequence id) {
        return new DeleteCacheEntryResponse(id, false);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
