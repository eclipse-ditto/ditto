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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import org.bson.BsonValue;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotCodec;
import org.eclipse.ditto.json.JsonObject;

/**
 * MongoDB BSON snapshot storage-envelope codec.
 * <p>
 * Encapsulates the only MongoDB-specific step in snapshot (de)serialization: converting the backend-neutral
 * {@link JsonObject} produced by the shared {@code SnapshotSerializer} into a {@link BsonValue} for storage
 * (via {@link DittoBsonJson#parse(JsonObject)}), and recovering it back (via
 * {@link DittoBsonJson#serialize(BsonValue)}).
 * <p>
 * This is the single source of the BSON envelope that was previously duplicated between
 * {@link AbstractMongoSnapshotAdapter#toSnapshotStore} and its {@code convertToJson} helper. Both now delegate
 * here; the behaviour is identical, so {@code SnapshotSerializerParityTest} remains the safety net.
 *
 * @since 3.7.0
 */
@Immutable
public final class MongoSnapshotCodec implements SnapshotCodec {

    /**
     * Singleton instance — stateless and thread-safe.
     */
    public static final MongoSnapshotCodec INSTANCE = new MongoSnapshotCodec();

    private MongoSnapshotCodec() {
        // singleton
    }

    /**
     * Encodes the neutral {@link JsonObject} into a {@link BsonValue} (specifically a {@link org.bson.BsonDocument})
     * for storage in the MongoDB snapshot store.
     *
     * @param json the snapshot rendered as a {@link JsonObject} by the shared serializer.
     * @return the BSON document to hand to the Pekko MongoDB snapshot store.
     * @throws NullPointerException if {@code json} is {@code null}.
     */
    @Override
    public Object encode(final JsonObject json) {
        checkNotNull(json, "json");
        return DittoBsonJson.getInstance().parse(json);
    }

    /**
     * Decodes the raw BSON snapshot value loaded from the MongoDB snapshot store back into the neutral
     * {@link JsonObject} representation.
     *
     * @param rawSnapshot the raw snapshot value as loaded from the store — must be a {@link BsonValue}.
     * @return the decoded {@link JsonObject}.
     * @throws NullPointerException     if {@code rawSnapshot} is {@code null}.
     * @throws IllegalArgumentException if {@code rawSnapshot} is not a {@link BsonValue}.
     * @throws DittoJsonException       if the {@link BsonValue} cannot be serialized to a {@link JsonObject}.
     */
    @Override
    public JsonObject decode(final Object rawSnapshot) {
        checkNotNull(rawSnapshot, "rawSnapshot");
        if (rawSnapshot instanceof BsonValue bsonValue) {
            return DittoJsonException.wrapJsonRuntimeException(
                    () -> DittoBsonJson.getInstance().serialize(bsonValue).asObject());
        }
        final String pattern = "Unable to decode snapshot from <{0}>! Expected a BsonValue instance.";
        throw new IllegalArgumentException(MessageFormat.format(pattern, rawSnapshot.getClass()));
    }
}
