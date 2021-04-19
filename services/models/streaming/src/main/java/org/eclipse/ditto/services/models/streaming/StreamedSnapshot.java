/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.streaming;

import java.util.Objects;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.entity.type.EntityType;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Serializable message for streamed snapshots.
 */
public final class StreamedSnapshot implements StreamingMessage, Jsonifiable<JsonObject> {

    private final EntityId entityId;
    private final JsonObject snapshot;

    private StreamedSnapshot(final EntityId entityId, final JsonObject snapshot) {
        this.entityId = entityId;
        this.snapshot = snapshot;
    }

    /**
     * Create a streamed snapshot from entity ID and snapshot as JSON object.
     *
     * @param entityId the entity ID of the snapshot.
     * @param snapshot the snapshot as JSON object.
     * @return the streamed snapshot object.
     */
    public static StreamedSnapshot of(final EntityId entityId, final JsonObject snapshot) {
        return new StreamedSnapshot(entityId, snapshot);
    }

    /**
     * Deserialize a streamed snapshot from JSON.
     *
     * @param jsonObject the JSON representation of the streamed snapshot.
     * @return the streamed snapshot object.
     */
    public static StreamedSnapshot fromJson(final JsonObject jsonObject) {
        final EntityType entityType = EntityType.of(jsonObject.getValueOrThrow(JsonFields.ENTITY_TYPE));
        final EntityId entityId = EntityId.of(entityType, jsonObject.getValueOrThrow(JsonFields.ENTITY_ID));
        final JsonObject snapshot = jsonObject.getValueOrThrow(JsonFields.SNAPSHOT);
        return new StreamedSnapshot(entityId, snapshot);
    }

    /**
     * Retrieve the entity ID of the streamed snapshot.
     *
     * @return the entity ID.
     */
    public EntityId getEntityId() {
        return entityId;
    }

    /**
     * Retrieve the snapshot as JSON object.
     *
     * @return the snapshot as JSON object.
     */
    public JsonObject getSnapshot() {
        return snapshot;
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JsonFields.ENTITY_TYPE, entityId.getEntityType().toString())
                .set(JsonFields.ENTITY_ID, entityId.toString())
                .set(JsonFields.SNAPSHOT, snapshot)
                .build();
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof StreamedSnapshot) {
            final StreamedSnapshot that = (StreamedSnapshot) other;
            return Objects.equals(entityId, that.entityId) &&
                    Objects.equals(snapshot, that.snapshot);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, snapshot);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[entityId=" + entityId +
                ",snapshot=" + snapshot +
                "]";
    }

    private static final class JsonFields {

        private static final JsonFieldDefinition<String> ENTITY_TYPE = JsonFactory.newStringFieldDefinition("type");

        private static final JsonFieldDefinition<String> ENTITY_ID = JsonFactory.newStringFieldDefinition("id");

        private static final JsonFieldDefinition<JsonObject> SNAPSHOT = JsonFactory.newJsonObjectFieldDefinition("s");
    }
}
