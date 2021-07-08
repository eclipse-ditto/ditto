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
package org.eclipse.ditto.internal.models.streaming;

import java.util.Objects;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;

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
     * Deserializes a {@code StreamedSnapshot} from the specified {@link JsonObject} argument.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @return the deserialized {@code StreamedSnapshot}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static StreamedSnapshot fromJson(final JsonObject jsonObject) {
        return new StreamedSnapshot(deserializeEntityId(jsonObject), jsonObject.getValueOrThrow(JsonFields.SNAPSHOT));
    }

    private static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                JsonFields.ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, JsonFields.ENTITY_TYPE));
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
