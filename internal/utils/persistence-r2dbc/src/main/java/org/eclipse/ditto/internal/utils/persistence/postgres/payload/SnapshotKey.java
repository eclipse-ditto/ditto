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

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.json.JsonValue;

/**
 * The primary key of a {@code <e>_snaps} row, {@code (pid, sn, written_at)}, and its rendering as the opaque cursor of
 * a {@link PostgresPersistedSnapshot}: a small JSON object {@code {"sn": …, "writtenAt": "…", "pid": "…"}}. JSON
 * carries any character a persistence id may contain, and {@link Instant#toString()} round-trips the microsecond
 * precision PostgreSQL stores a {@code TIMESTAMPTZ} with. The store parses the cursor back into exactly the key, so a
 * resumed stream continues strictly behind the row the cursor names.
 *
 * @param pid the persistence id.
 * @param sn the sequence number.
 * @param writtenAt the {@code written_at} of the row, as PostgreSQL stores it.
 */
record SnapshotKey(String pid, long sn, Instant writtenAt) {

    static final String PID = "pid";
    static final String SN = "sn";
    static final String WRITTEN_AT = "writtenAt";

    SnapshotKey {
        checkNotNull(pid, "pid");
        checkNotNull(writtenAt, "writtenAt");
    }

    /**
     * @return this key as the cursor string.
     */
    String toCursor() {
        return JsonFactory.newObjectBuilder()
                .set(SN, sn)
                .set(WRITTEN_AT, writtenAt.toString())
                .set(PID, pid)
                .build()
                .toString();
    }

    /**
     * Parses a cursor produced by {@link #toCursor()}.
     *
     * @param cursor the cursor.
     * @return the key it encodes.
     * @throws IllegalArgumentException if the cursor is not one this store produced; the message names the cursor.
     */
    static SnapshotKey fromCursor(final String cursor) {
        checkNotNull(cursor, "cursor");
        try {
            final JsonObject json = JsonFactory.newObject(cursor);
            final long sn = json.getValue(SN)
                    .filter(JsonValue::isLong)
                    .map(JsonValue::asLong)
                    .orElseThrow(() -> new IllegalArgumentException("missing or non-integral <" + SN + ">"));
            final Instant writtenAt = Instant.parse(json.getValue(WRITTEN_AT)
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .orElseThrow(() -> new IllegalArgumentException("missing <" + WRITTEN_AT + ">")));
            final String pid = json.getValue(PID)
                    .filter(JsonValue::isString)
                    .map(JsonValue::asString)
                    .orElseThrow(() -> new IllegalArgumentException("missing <" + PID + ">"));
            return new SnapshotKey(pid, sn, writtenAt);
        } catch (final JsonRuntimeException | DateTimeParseException | IllegalArgumentException e) {
            throw new IllegalArgumentException("The snapshot cursor <" + cursor + "> is not one the PostgreSQL "
                    + "payload store produced (expected a JSON object with <" + SN + ">, <" + WRITTEN_AT + "> and <"
                    + PID + ">): " + e.getMessage(), e);
        }
    }

}
