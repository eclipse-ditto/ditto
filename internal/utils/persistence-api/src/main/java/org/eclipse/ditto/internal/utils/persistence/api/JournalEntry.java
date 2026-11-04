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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

/**
 * Backend-neutral representation of a journal entry as streamed by
 * {@link DittoReadJournal#getLatestJournalEntries}. Carries the well-known fields consumers need
 * (persistence id, manifest) decoded into plain Java types so that no backend driver type
 * (e.g. {@code org.bson.Document}) crosses the {@link DittoReadJournal} interface boundary.
 *
 * @since 4.0.0
 */
@Immutable
public final class JournalEntry {

    @Nullable private final String pid;
    @Nullable private final String manifest;
    private final JsonObject json;

    private JournalEntry(@Nullable final String pid, @Nullable final String manifest, final JsonObject json) {
        this.pid = pid;
        this.manifest = manifest;
        this.json = json;
    }

    /**
     * Creates a journal entry.
     *
     * @param pid the persistence id of the entry, or {@code null} if absent.
     * @param manifest the event manifest (type) of the entry, or {@code null} if absent.
     * @param json the full entry payload as a backend-neutral JSON object.
     * @return the journal entry.
     */
    public static JournalEntry of(@Nullable final String pid, @Nullable final String manifest,
            final JsonObject json) {
        return new JournalEntry(pid, manifest, Objects.requireNonNull(json, "json"));
    }

    /**
     * @return the persistence id of the entry, if present.
     */
    public Optional<String> getPid() {
        return Optional.ofNullable(pid);
    }

    /**
     * @return the event manifest (fully-qualified event type) of the entry, if present.
     */
    public Optional<String> getManifest() {
        return Optional.ofNullable(manifest);
    }

    /**
     * @return the full entry payload as a backend-neutral JSON object.
     */
    public JsonObject getJson() {
        return json;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JournalEntry)) {
            return false;
        }
        final JournalEntry that = (JournalEntry) o;
        return Objects.equals(pid, that.pid) && Objects.equals(manifest, that.manifest) &&
                Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, manifest, json);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                " [pid=" + pid +
                ", manifest=" + manifest +
                ", json=" + json +
                ']';
    }
}
