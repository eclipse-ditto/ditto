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
package org.eclipse.ditto.services.models.concierge.cache;

/**
 * Cache entry for authorization.
 */
public interface Entry<T> {

    /**
     * Revision of the cache entry. An entry may only override those with smaller revisions.
     *
     * @return the revision number.
     */
    long getRevision();

    /**
     * Retrieve the value if present.
     *
     * @return the cached value if present.
     */
    T getValue();

    boolean exists();

    static <T> Entry<T> permanent(final T value) {
        return new ExistentEntry<>(Long.MAX_VALUE, value);
    }

    static <T> Entry<T> of(final long revision, final T value) {
        return new ExistentEntry<>(revision, value);
    }

    static <T> Entry<T> nonexistent() {
        return NonexistentEntry.getInstance();
    }
}
