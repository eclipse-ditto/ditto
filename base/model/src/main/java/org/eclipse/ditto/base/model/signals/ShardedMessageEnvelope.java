/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Objects;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.EntityIdJsonDeserializer;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityTypeJsonDeserializer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;

/**
 * A message envelope for messages to PersistenceActors which do not contain itself an ID. Holds both an ID and the
 * message (message) which should be delivered to the PersistenceActor.
 */
public final class ShardedMessageEnvelope
        implements Jsonifiable<JsonObject>, DittoHeadersSettable<ShardedMessageEnvelope>, WithEntityId {

    /**
     * JSON field containing the identifier of a {@code ShardedMessageEnvelope}.
     */
    public static final JsonFieldDefinition<String> JSON_ID = JsonFactory.newStringFieldDefinition("id");


    /**
     * JSON field containing the type of the entity the id identifies.
     */
    public static final JsonFieldDefinition<String> JSON_ID_TYPE = JsonFactory.newStringFieldDefinition("entityType");

    /**
     * JSON field containing the type of the message of a {@code ShardedMessageEnvelope}.
     */
    public static final JsonFieldDefinition<String> JSON_TYPE = JsonFactory.newStringFieldDefinition("type");

    /**
     * JSON field containing the message of a {@code ShardedMessageEnvelope}.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_MESSAGE =
            JsonFactory.newJsonObjectFieldDefinition("message");

    /**
     * JSON field containing the {@code DittoHeaders} of a {@code ShardedMessageEnvelope}.
     */
    public static final JsonFieldDefinition<JsonObject> JSON_DITTO_HEADERS =
            JsonFactory.newJsonObjectFieldDefinition("dittoHeaders");

    private final EntityId id;
    private final String type;
    private final JsonObject message;
    private final DittoHeaders dittoHeaders;

    private ShardedMessageEnvelope(final EntityId id,
            final String type,
            final JsonObject message,
            final DittoHeaders dittoHeaders) {

        this.id = checkNotNull(id, "Message ID");
        this.type = checkNotNull(type, "Type");
        this.message = checkNotNull(message, "Message");
        this.dittoHeaders = checkNotNull(dittoHeaders, "Command Headers");
    }

    /**
     * Returns a new {@code ShardedMessageEnvelope} for the specified {@code id} and {@code message}.
     *
     * @param id the identifier.
     * @param type the type of the message.
     * @param message the message.
     * @param dittoHeaders the command headers.
     * @return the ShardedMessageEnvelope.
     */
    public static ShardedMessageEnvelope of(final EntityId id,
            final String type,
            final JsonObject message,
            final DittoHeaders dittoHeaders) {

        return new ShardedMessageEnvelope(id, type, message, dittoHeaders);
    }

    /**
     * Returns a new {@code ShardedMessageEnvelope} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object to be deserialized.
     * @return the deserialized {@code ShardedMessageEnvelope}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} did not contain all required
     * fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} was not in the expected format.
     */
    public static ShardedMessageEnvelope fromJson(final JsonObject jsonObject) {
        return of(deserializeEntityId(jsonObject),
                jsonObject.getValueOrThrow(JSON_TYPE),
                jsonObject.getValueOrThrow(JSON_MESSAGE),
                deserializeDittoHeaders(jsonObject));
    }

    private static EntityId deserializeEntityId(final JsonObject jsonObject) {
        return EntityIdJsonDeserializer.deserializeEntityId(jsonObject,
                JSON_ID,
                EntityTypeJsonDeserializer.deserializeEntityType(jsonObject, JSON_ID_TYPE));
    }

    private static DittoHeaders deserializeDittoHeaders(final JsonObject jsonObject) {
        final JsonFieldDefinition<JsonObject> fieldDefinition = JSON_DITTO_HEADERS;
        final JsonObject jsonDittoHeaders = jsonObject.getValueOrThrow(fieldDefinition);
        try {
            return DittoHeaders.newBuilder(jsonDittoHeaders).build();
        } catch (final RuntimeException e) {
            throw JsonParseException.newBuilder()
                    .message(MessageFormat.format("Failed to deserialize value of key <{0}> as {1}: {2}",
                            fieldDefinition.getPointer(),
                            DittoHeaders.class.getSimpleName(),
                            e.getMessage()))
                    .cause(e)
                    .build();
        }
    }

    /**
     * Returns the ID of the envelope.
     *
     * @return the ID of the envelope.
     */
    @Override
    public EntityId getEntityId() {
        return id;
    }

    /**
     * Returns the type of the message.
     *
     * @return the type of the message.
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the message of the envelope.
     *
     * @return the message of the envelope.
     */
    public JsonObject getMessage() {
        return message;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public ShardedMessageEnvelope setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(id, type, message, dittoHeaders);
    }

    @Override
    public JsonObject toJson() {
        return JsonObject.newBuilder()
                .set(JSON_ID_TYPE, id.getEntityType().toString())
                .set(JSON_ID, String.valueOf(id))
                .set(JSON_TYPE, type)
                .set(JSON_MESSAGE, message)
                .set(JSON_DITTO_HEADERS, dittoHeaders.toJson())
                .build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ShardedMessageEnvelope that = (ShardedMessageEnvelope) o;
        return Objects.equals(id, that.id) && Objects.equals(type, that.type) && Objects.equals(message, that.message)
                && Objects.equals(dittoHeaders, that.dittoHeaders);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, message, dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "id=" + id + ", type=" + type + ", message=" + message
                + ", dittoHeaders=" + dittoHeaders + "]";
    }

}
