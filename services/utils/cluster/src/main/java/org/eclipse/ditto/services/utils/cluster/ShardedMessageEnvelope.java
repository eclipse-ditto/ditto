/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * A message envelope for messages to PersistenceActors which do not contain itself an ID. Holds both an ID and the
 * message (message) which should be delivered to the PersistenceActor.
 */
public final class ShardedMessageEnvelope
        implements Jsonifiable<JsonObject>, WithDittoHeaders<ShardedMessageEnvelope> {

    /**
     * JSON field containing the identifier of a {@code ShardedMessageEnvelope}.
     */
    public static final JsonFieldDefinition<String> JSON_ID = JsonFactory.newStringFieldDefinition("id");

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

    private final String id;
    private final String type;
    private final JsonObject message;
    private final DittoHeaders dittoHeaders;

    private ShardedMessageEnvelope(final String id,
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
    public static ShardedMessageEnvelope of(final String id,
            final String type,
            final JsonObject message,
            final DittoHeaders dittoHeaders) {

        return new ShardedMessageEnvelope(id, type, message, dittoHeaders);
    }

    /**
     * Returns a new {@code ShardedMessageEnvelope} parsed from the specified {@code jsonObject}.
     *
     * @param jsonObject the JSON object.
     * @return the ShardedMessageEnvelope.
     */
    public static ShardedMessageEnvelope fromJson(final JsonObject jsonObject) {
        final String extractedId = jsonObject.getValueOrThrow(JSON_ID);
        final String extractedType = jsonObject.getValueOrThrow(JSON_TYPE);
        final JsonObject extractedMessage = jsonObject.getValueOrThrow(JSON_MESSAGE);
        final JsonObject jsonDittoHeaders = jsonObject.getValueOrThrow(JSON_DITTO_HEADERS);
        final DittoHeaders extractedDittoHeaders = DittoHeaders.newBuilder(jsonDittoHeaders).build();

        return of(extractedId, extractedType, extractedMessage, extractedDittoHeaders);
    }

    /**
     * Returns the ID of the envelope.
     *
     * @return the ID of the envelope.
     */
    public String getId() {
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
                .set(JSON_ID, id)
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
