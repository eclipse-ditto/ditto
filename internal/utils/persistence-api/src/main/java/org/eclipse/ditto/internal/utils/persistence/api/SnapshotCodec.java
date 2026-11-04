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

import org.eclipse.ditto.json.JsonObject;

/**
 * The backend-specific snapshot storage-envelope abstraction: the only thing that differs between backends when
 * (de)serializing a snapshot.
 * <p>
 * The pure domain&harr;{@link JsonObject} logic of a snapshot (deleted-lifecycle detection, revision handling, parsing)
 * already lives in the backend-neutral {@code SnapshotSerializer} and is parity-tested by
 * {@code SnapshotSerializerParityTest}. What remains backend-specific is purely how that {@link JsonObject} is wrapped for
 * storage: MongoDB encodes it to a {@code org.bson.BsonValue}, PostgreSQL renders it to JSONB text. This codec captures
 * exactly that envelope so a snapshot adapter can be assembled from {@code SnapshotSerializer} (shared) plus a
 * {@code SnapshotCodec} (per backend).
 * <p>
 * It is intentionally backend-neutral: {@link #encode(JsonObject)} returns the store payload as an opaque
 * {@link Object} (the Pekko snapshot store accepts an arbitrary snapshot object — a {@code BsonValue} for Mongo, a JSONB
 * {@code String} for Postgres) and {@link #decode(Object)} accepts the raw value the store loaded back. Neither
 * signature names a backend type, so this interface stays free of {@code org.bson}, {@code io.r2dbc} and
 * {@code org.postgresql}. The Mongo implementation is task B5, the Postgres implementation task E2.
 *
 * @since 3.7.0
 */
public interface SnapshotCodec {

    /**
     * Encodes the neutral {@link JsonObject} snapshot representation into the backend's storage envelope — the value
     * handed to the Pekko snapshot store (a {@code BsonValue} for Mongo, JSONB text for Postgres).
     *
     * @param json the snapshot rendered as a {@link JsonObject} by the shared serializer.
     * @return the backend-specific store payload (opaque to this neutral module).
     * @throws NullPointerException if {@code json} is {@code null}.
     */
    Object encode(JsonObject json);

    /**
     * Decodes the raw snapshot value loaded from the backend store back into the neutral {@link JsonObject}
     * representation the shared serializer consumes.
     *
     * @param rawSnapshot the raw snapshot value as loaded from the store (e.g. a {@code BsonValue}, a JSONB
     * {@code String} or {@code byte[]}).
     * @return the decoded {@link JsonObject}.
     * @throws NullPointerException if {@code rawSnapshot} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshot} is not a shape this backend can decode.
     */
    JsonObject decode(Object rawSnapshot);
}
