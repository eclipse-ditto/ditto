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
import java.util.Optional;
import java.util.OptionalLong;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Backend-neutral representation of a snapshot entry as streamed by
 * {@link DittoReadJournal#getNewestSnapshotsAbove}. Carries the well-known fields consumers need
 * (persistence id, sequence number, lifecycle) decoded into plain Java types, plus the projected
 * snapshot payload as a backend-neutral JSON object, so that no backend driver type
 * (e.g. {@code org.bson.Document}) crosses the {@link DittoReadJournal} interface boundary.
 *
 * @since 4.0.0
 */
@Immutable
public final class SnapshotEntry {

    @Nullable private final String pid;
    @Nullable private final Long sequenceNumber;
    @Nullable private final String lifecycle;
    private final JsonObject json;

    private SnapshotEntry(@Nullable final String pid,
            @Nullable final Long sequenceNumber,
            @Nullable final String lifecycle,
            final JsonObject json) {
        this.pid = pid;
        this.sequenceNumber = sequenceNumber;
        this.lifecycle = lifecycle;
        this.json = json;
    }

    /**
     * Creates a snapshot entry.
     *
     * @param pid the persistence id of the snapshot, or {@code null} if absent.
     * @param sequenceNumber the snapshot sequence number, or {@code null} if absent.
     * @param lifecycle the snapshot lifecycle marker (e.g. {@code "DELETED"}), or {@code null} if absent.
     * @param json the projected snapshot payload as a backend-neutral JSON object (without the persistence id field).
     * @return the snapshot entry.
     */
    public static SnapshotEntry of(@Nullable final String pid,
            @Nullable final Long sequenceNumber,
            @Nullable final String lifecycle,
            final JsonObject json) {
        return new SnapshotEntry(pid, sequenceNumber, lifecycle, Objects.requireNonNull(json, "json"));
    }

    /**
     * @return the persistence id of the snapshot, if present.
     */
    public Optional<String> getPid() {
        return Optional.ofNullable(pid);
    }

    /**
     * @return the snapshot sequence number, if present.
     */
    public OptionalLong getSequenceNumber() {
        return sequenceNumber == null ? OptionalLong.empty() : OptionalLong.of(sequenceNumber);
    }

    /**
     * @return the snapshot lifecycle marker (e.g. {@code "DELETED"}), if present.
     */
    public Optional<String> getLifecycle() {
        return Optional.ofNullable(lifecycle);
    }

    /**
     * @return whether the snapshot is in the {@code DELETED} lifecycle state.
     */
    public boolean isDeleted() {
        return "DELETED".equals(lifecycle);
    }

    /**
     * @return the projected snapshot payload as a backend-neutral JSON object (without the persistence id field).
     */
    public JsonObject getJson() {
        return json;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SnapshotEntry)) {
            return false;
        }
        final SnapshotEntry that = (SnapshotEntry) o;
        return Objects.equals(pid, that.pid) && Objects.equals(sequenceNumber, that.sequenceNumber) &&
                Objects.equals(lifecycle, that.lifecycle) && Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, sequenceNumber, lifecycle, json);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [pid=" + pid +
                ", sequenceNumber=" + sequenceNumber +
                ", lifecycle=" + lifecycle +
                ", json=" + json +
                ']';
    }
}
