/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.akka;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Internal simple "ping" command to be sent to PersistenceActors (identified by the contained {@code entityId})
 * which is Jsonifiable and has an optional correlationId and optionally payload.
 */
@Immutable
public final class PingCommand implements Jsonifiable<JsonObject>, WithEntityId {

    private final EntityId entityId;
    @Nullable private final String correlationId;
    @Nullable private final JsonValue payload;

    private PingCommand(final EntityId entityId,
            @Nullable final String correlationId,
            @Nullable final JsonValue payload) {

        this.entityId = entityId;
        this.correlationId = correlationId;
        this.payload = payload;
    }

    /**
     * Returns a new {@code PingCommand} instance.
     *
     * @param entityId the Entity ID to send the ping command to.
     * @param correlationId an optional identifier correlating a PingCommand to a PingCommandResponse.
     * @param payload optional payload to transmit with the PingCommand.
     * @return the new PingCommand instance.
     * @throws NullPointerException if {@code entityId} is {@code null}.
     */
    public static PingCommand of(final EntityId entityId,
            @Nullable final String correlationId,
            @Nullable final JsonValue payload) {

        return new PingCommand(ConditionChecker.checkNotNull(entityId, "entityId"), correlationId, payload);
    }

    /**
     * Deserializes a {@code PingCommand} from the specified JSON object string argument.
     *
     * @param jsonString string representation of the JSON object to be deserialized.
     * @return the deserialized {@code PingCommand}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonString} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} was not in the expected format.
     */
    public static PingCommand fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Deserializes a {@code PingCommand} from the specified {@link JsonObject} argument.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @return the deserialized {@code PingCommand}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static PingCommand fromJson(final JsonObject jsonObject) {
        return of(deserializeEntityId(jsonObject),
                jsonObject.getValue(JsonFields.CORRELATION_ID).orElse(null),
                jsonObject.getValue(JsonFields.PAYLOAD).orElse(null));
    }

    private static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                JsonFields.ENTITY_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, JsonFields.ENTITY_TYPE));
    }

    @Override
    public EntityId getEntityId() {
        return entityId;
    }

    /**
     * Returns the optional correlation ID.
     *
     * @return the optional correlation ID.
     */
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Returns the optional JSON payload.
     *
     * @return the optional JSON payload.
     */
    public Optional<JsonValue> getPayload() {
        return Optional.ofNullable(payload);
    }

    @Override
    public JsonObject toJson() {
        final var jsonObjectBuilder = JsonObject.newBuilder()
                .set(JsonFields.ENTITY_TYPE, entityId.getEntityType().toString())
                .set(JsonFields.ENTITY_ID, entityId.toString());
        if (null != correlationId) {
            jsonObjectBuilder.set(JsonFields.CORRELATION_ID, correlationId);
        }
        if (null != payload) {
            jsonObjectBuilder.set(JsonFields.PAYLOAD, payload);
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (PingCommand) o;
        return Objects.equals(entityId, that.entityId) &&
                Objects.equals(correlationId, that.correlationId) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, correlationId, payload);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "entityId=" + entityId +
                ", correlationId=" + correlationId +
                ", payload=" + payload +
                "]";
    }

    /**
     * An enumeration of the known {@link JsonFieldDefinition}s of a PingCommand.
     */
    @Immutable
    public static final class JsonFields {

        /**
         * JSON field containing the entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_TYPE = JsonFactory.newStringFieldDefinition("entityType");

        /**
         * JSON field containing the entity ID.
         */
        static final JsonFieldDefinition<String> ENTITY_ID = JsonFactory.newStringFieldDefinition("entityId");

        /**
         * JSON field containing the correlationId.
         */
        static final JsonFieldDefinition<String> CORRELATION_ID = JsonFactory.newStringFieldDefinition("correlationId");

        /**
         * JSON field containing optional payload.
         */
        static final JsonFieldDefinition<JsonValue> PAYLOAD = JsonFactory.newJsonValueFieldDefinition("payload");

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
