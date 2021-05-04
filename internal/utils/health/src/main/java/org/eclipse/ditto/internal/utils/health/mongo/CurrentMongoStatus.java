/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.health.mongo;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * Reflects the currently determined MongoDB health status.
 * Whether {@code alive} or not including a description.
 */
@Immutable
public final class CurrentMongoStatus {

    private final boolean alive;
    private final String description;

    /**
     * Constructs a new {@code CurrentMongoStatus} object.
     *
     * @param alive indicates whether the Persistence is alive.
     */
    public CurrentMongoStatus(final boolean alive) {
        this(alive, null);
    }

    /**
     * Constructs a new {@code CurrentMongoStatus} object.
     *
     * @param alive indicates whether the Persistence is alive.
     * @param description an optional description why it is alive/not alive.
     */
    public CurrentMongoStatus(final boolean alive, final String description) {
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
        final CurrentMongoStatus that = (CurrentMongoStatus) o;
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
