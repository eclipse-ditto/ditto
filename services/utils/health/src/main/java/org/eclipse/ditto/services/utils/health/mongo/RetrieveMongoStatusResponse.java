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
package org.eclipse.ditto.services.utils.health.mongo;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Response to the {@link RetrieveMongoStatus} command.
 */
@Immutable
public final class RetrieveMongoStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean alive;
    private final String description;

    /**
     * Constructs a new {@code RetrieveMongoStatusResponse} object.
     *
     * @param alive indicates whether the Persistence is alive.
     */
    public RetrieveMongoStatusResponse(final boolean alive) {
        this(alive, null);
    }

    /**
     * Constructs a new {@code RetrieveMongoStatusResponse} object.
     *
     * @param alive indicates whether the Persistence is alive.
     * @param description an optional description why it is alive/not alive.
     */
    public RetrieveMongoStatusResponse(final boolean alive, final String description) {
        this.alive = alive;
        this.description = description;
    }

    /**
     * Returns whether the Persistence is alive.
     *
     * @return whether the Persistence is alive.
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * Returns an optional description why it is alive/not alive.
     *
     * @return an optional description why it is alive/not alive.
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveMongoStatusResponse that = (RetrieveMongoStatusResponse) o;
        return alive == that.alive && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alive, description);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "alive=" + alive + ", description=" + description + "]";
    }
}
