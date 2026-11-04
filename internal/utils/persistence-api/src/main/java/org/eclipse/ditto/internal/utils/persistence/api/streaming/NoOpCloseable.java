/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.api.streaming;

import java.io.Closeable;

/**
 * A {@link Closeable} whose {@link #close()} does nothing.
 * <p>
 * Used by streaming actors that are wired with a shared, externally-owned read journal (and thus a resource the actor
 * must not close), and by backends — such as PostgreSQL — whose connection pool is owned elsewhere (e.g. a client
 * extension) and outlives the actor.
 *
 * @since 4.0.0
 */
public final class NoOpCloseable implements Closeable {

    private static final NoOpCloseable INSTANCE = new NoOpCloseable();

    private NoOpCloseable() {
        // singleton
    }

    /**
     * @return the shared no-op {@link Closeable} instance.
     */
    public static NoOpCloseable getInstance() {
        return INSTANCE;
    }

    @Override
    public void close() {
        // intentionally does nothing: the resource is owned elsewhere.
    }
}
