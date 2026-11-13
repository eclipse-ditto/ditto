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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

/**
 * PostgreSQL JSONB snapshot storage-envelope codec — the Postgres mirror of {@code MongoSnapshotCodec}.
 * <p>
 * Encapsulates the only PostgreSQL-specific step in snapshot (de)serialization: rendering the backend-neutral
 * {@link JsonObject} produced by the shared {@code SnapshotSerializer} to JSONB text for storage, and recovering it from
 * whatever shape the JSONB column surfaces on load. The pure domain&harr;{@link JsonObject} logic stays in the shared
 * serializer; only this envelope differs from MongoDB's BSON envelope.
 * <p>
 * This is the single source of the JSONB envelope: {@code NeutralSnapshotAdapter} composes it with the per-service
 * {@code SnapshotSerializer}, so no PostgreSQL-specific snapshot adapter exists.
 *
 * @since 4.0.0
 */
@Immutable
public final class PostgresSnapshotCodec implements SnapshotCodec {

    /**
     * Singleton instance — stateless and thread-safe.
     */
    public static final PostgresSnapshotCodec INSTANCE = new PostgresSnapshotCodec();

    private PostgresSnapshotCodec() {
        // singleton
    }

    /**
     * Encodes the neutral {@link JsonObject} into the JSONB store value the r2dbc driver writes — the JSON text bound to
     * the {@code ::jsonb} column parameter (see {@code PostgresPersistenceOperations.saveSnapshot}, which binds
     * {@code $3::jsonb}). Returned as an opaque {@link Object} (a {@link String}) per the {@link SnapshotCodec} contract.
     *
     * @param json the snapshot rendered as a {@link JsonObject} by the shared serializer.
     * @return the JSONB store payload (JSON text) to hand to the PostgreSQL snapshot store.
     * @throws NullPointerException if {@code json} is {@code null}.
     */
    @Override
    public Object encode(final JsonObject json) {
        checkNotNull(json, "json");
        return json.toString();
    }

    /**
     * Decodes the raw JSONB snapshot value loaded from the PostgreSQL snapshot store back into the neutral
     * {@link JsonObject} representation. Accepts the shapes the r2dbc PostgreSQL driver can surface for a JSONB column:
     * an already-parsed {@link JsonObject}, a {@link CharSequence} (JSONB text), or {@code byte[]} (UTF-8 JSONB bytes).
     *
     * @param rawSnapshot the raw snapshot value as loaded from the store.
     * @return the decoded {@link JsonObject}.
     * @throws NullPointerException if {@code rawSnapshot} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshot} is not a JSONB-decodable shape.
     */
    @Override
    public JsonObject decode(final Object rawSnapshot) {
        checkNotNull(rawSnapshot, "rawSnapshot");
        if (rawSnapshot instanceof JsonObject jsonObject) {
            return jsonObject;
        }
        if (rawSnapshot instanceof CharSequence charSequence) {
            return JsonFactory.newObject(charSequence.toString());
        }
        if (rawSnapshot instanceof byte[] bytes) {
            return JsonFactory.newObject(new String(bytes, StandardCharsets.UTF_8));
        }
        final String pattern =
                "Unable to decode snapshot from <{0}>! Expected a JSONB-decodable value (JsonObject, String or byte[]).";
        throw new IllegalArgumentException(MessageFormat.format(pattern, rawSnapshot.getClass()));
    }
}
