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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link PersistedJournalEntry} over one row of a {@code <e>_journal} table. PostgreSQL stores one event per row, so
 * the entry holds exactly one event payload: the JSON text of the row's {@code event JSONB} column. The cursor is the
 * row's {@code seq} (the monotonic identity column) as a decimal string.
 * <p>
 * {@link #withEventPayloads(List)} takes exactly one payload. A payload equal to the stored one leaves the row
 * untouched: the entry then carries no rewritten text and {@link PostgresPersistedPayloadStore#rewriteJournalEntries}
 * issues no statement for it, as the MongoDB store leaves an unchanged event document alone. A changed payload is taken
 * through {@link JsonTextPayloadCodec}, the codec every PostgreSQL write goes through (a payload with U+0000 is
 * rejected before anything is written).
 *
 * @since 4.0.0
 */
public final class PostgresPersistedJournalEntry implements PersistedJournalEntry {

    private final String pid;
    private final long sn;
    private final long seq;
    private final String storedEventJson;
    @Nullable
    private final String rewrittenEventJson;

    private PostgresPersistedJournalEntry(final String pid, final long sn, final long seq,
            final String storedEventJson, @Nullable final String rewrittenEventJson) {
        this.pid = pid;
        this.sn = sn;
        this.seq = seq;
        this.storedEventJson = storedEventJson;
        this.rewrittenEventJson = rewrittenEventJson;
    }

    /**
     * Wraps a journal row.
     *
     * @param pid the row's persistence id.
     * @param sn the row's sequence number.
     * @param seq the row's {@code seq}.
     * @param eventJson the JSON text of the row's {@code event} column.
     * @return the wrapper.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PostgresPersistedJournalEntry of(final String pid, final long sn, final long seq, final String eventJson) {
        return new PostgresPersistedJournalEntry(checkNotNull(pid, "pid"), sn, seq,
                checkNotNull(eventJson, "eventJson"), null);
    }

    long sn() {
        return sn;
    }

    long seq() {
        return seq;
    }

    /**
     * @return the JSON text of the stored event, as read from the row.
     */
    String storedEventJson() {
        return storedEventJson;
    }

    /**
     * @return the JSON text {@link PostgresPersistedPayloadStore#rewriteJournalEntries} writes into the row, or empty
     * if this entry's payload equals the stored one and the row is to be left untouched.
     */
    Optional<String> rewrittenEventJson() {
        return Optional.ofNullable(rewrittenEventJson);
    }

    @Override
    public String cursor() {
        return Long.toString(seq);
    }

    @Override
    public String pid() {
        return pid;
    }

    @Override
    public List<JsonObject> eventPayloads() {
        return List.of(JsonFactory.newObject(rewrittenEventJson != null ? rewrittenEventJson : storedEventJson));
    }

    @Override
    public PersistedJournalEntry withEventPayloads(final List<JsonObject> payloads) {
        checkNotNull(payloads, "payloads");
        if (payloads.size() != 1) {
            throw new IllegalArgumentException("The journal entry <" + cursor() + "> holds 1 event payload, but "
                    + payloads.size() + " were given to replace it.");
        }
        final JsonObject payload = checkNotNull(payloads.get(0), "payloads[0]");
        if (payload.equals(JsonFactory.newObject(storedEventJson))) {
            return new PostgresPersistedJournalEntry(pid, sn, seq, storedEventJson, null);
        }
        return new PostgresPersistedJournalEntry(pid, sn, seq, storedEventJson,
                JsonTextPayloadCodec.INSTANCE.encode(payload));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PostgresPersistedJournalEntry that = (PostgresPersistedJournalEntry) o;
        return sn == that.sn && seq == that.seq && pid.equals(that.pid)
                && storedEventJson.equals(that.storedEventJson)
                && Objects.equals(rewrittenEventJson, that.rewrittenEventJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, sn, seq, storedEventJson, rewrittenEventJson);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "pid=" + pid +
                ", sn=" + sn +
                ", seq=" + seq +
                ", rewritten=" + (rewrittenEventJson != null) +
                ']';
    }

}
