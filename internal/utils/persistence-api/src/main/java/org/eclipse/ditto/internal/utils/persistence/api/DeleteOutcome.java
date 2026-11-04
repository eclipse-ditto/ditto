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
package org.eclipse.ditto.internal.utils.persistence.api;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * Backend-neutral outcome of a delete operation on the read journal (event or snapshot deletion).
 * <p>
 * Replaces the previously leaked {@code com.mongodb.client.result.DeleteResult} so that no
 * MongoDB type crosses the {@link DittoReadJournal} interface boundary. Backends populate it from
 * their respective driver result (e.g. MongoDB's {@code DeleteResult}, a SQL {@code rowsUpdated}).
 *
 * @since 4.0.0
 */
@Immutable
public final class DeleteOutcome {

    private final boolean acknowledged;
    private final long deletedCount;

    private DeleteOutcome(final boolean acknowledged, final long deletedCount) {
        this.acknowledged = acknowledged;
        this.deletedCount = deletedCount;
    }

    /**
     * Creates an acknowledged delete outcome.
     *
     * @param deletedCount the number of deleted entries.
     * @return the outcome.
     */
    public static DeleteOutcome acknowledged(final long deletedCount) {
        return new DeleteOutcome(true, deletedCount);
    }

    /**
     * Creates a delete outcome.
     *
     * @param acknowledged whether the delete was acknowledged by the backend.
     * @param deletedCount the number of deleted entries (defined only if acknowledged).
     * @return the outcome.
     */
    public static DeleteOutcome of(final boolean acknowledged, final long deletedCount) {
        return new DeleteOutcome(acknowledged, deletedCount);
    }

    /**
     * @return whether the delete was acknowledged by the backend.
     */
    public boolean isAcknowledged() {
        return acknowledged;
    }

    /**
     * @return the number of deleted entries. Only meaningful when {@link #isAcknowledged()} is {@code true}.
     */
    public long getDeletedCount() {
        return deletedCount;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeleteOutcome)) {
            return false;
        }
        final DeleteOutcome that = (DeleteOutcome) o;
        return acknowledged == that.acknowledged && deletedCount == that.deletedCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(acknowledged, deletedCount);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [acknowledged=" + acknowledged +
                ", deletedCount=" + deletedCount +
                ']';
    }
}
