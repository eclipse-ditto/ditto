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
import java.util.Objects;

import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * A {@link PersistedSnapshot} over one row of a {@code <e>_snaps} table: the cursor is the row's primary key
 * {@code (pid, sn, written_at)} rendered by {@link SnapshotKey#toCursor()}, and the payload is the JSON text of its
 * {@code snapshot JSONB} column. {@link #withPayload(JsonObject)} keeps the key and takes the new JSON text through
 * {@link JsonTextPayloadCodec}, the codec every PostgreSQL write goes through (a payload with U+0000 is rejected before
 * anything is written), so {@link PostgresPersistedPayloadStore#rewriteSnapshots} can address exactly the stored row.
 *
 * @since 4.0.0
 */
public final class PostgresPersistedSnapshot implements PersistedSnapshot {

    private final SnapshotKey key;
    private final String snapshotJson;

    private PostgresPersistedSnapshot(final SnapshotKey key, final String snapshotJson) {
        this.key = key;
        this.snapshotJson = snapshotJson;
    }

    /**
     * Wraps a snapshot row.
     *
     * @param pid the row's persistence id.
     * @param sn the row's sequence number.
     * @param writtenAt the row's {@code written_at}.
     * @param snapshotJson the JSON text of the row's {@code snapshot} column.
     * @return the wrapper.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PostgresPersistedSnapshot of(final String pid, final long sn, final Instant writtenAt,
            final String snapshotJson) {
        return new PostgresPersistedSnapshot(new SnapshotKey(pid, sn, writtenAt),
                checkNotNull(snapshotJson, "snapshotJson"));
    }

    SnapshotKey key() {
        return key;
    }

    long sn() {
        return key.sn();
    }

    Instant writtenAt() {
        return key.writtenAt();
    }

    /**
     * @return the JSON text that {@link PostgresPersistedPayloadStore#rewriteSnapshots} writes into the row.
     */
    String snapshotJson() {
        return snapshotJson;
    }

    @Override
    public String cursor() {
        return key.toCursor();
    }

    @Override
    public String pid() {
        return key.pid();
    }

    @Override
    public JsonObject payload() {
        return JsonFactory.newObject(snapshotJson);
    }

    @Override
    public PersistedSnapshot withPayload(final JsonObject payload) {
        checkNotNull(payload, "payload");
        return new PostgresPersistedSnapshot(key, JsonTextPayloadCodec.INSTANCE.encode(payload));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PostgresPersistedSnapshot that = (PostgresPersistedSnapshot) o;
        return key.equals(that.key) && snapshotJson.equals(that.snapshotJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, snapshotJson);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "pid=" + key.pid() +
                ", sn=" + key.sn() +
                ", writtenAt=" + key.writtenAt() +
                ']';
    }

}
