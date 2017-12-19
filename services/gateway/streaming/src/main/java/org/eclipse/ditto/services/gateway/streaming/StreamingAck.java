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
package org.eclipse.ditto.services.gateway.streaming;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 * Send for acknowledging that a subscription in the cluster for a {@link StreamingType} was either subscribed to
 * ({@link #subscribed} is {@code true}) or unsubscribed from ({@link #subscribed} is {@code false}).
 */
public final class StreamingAck implements Jsonifiable.WithPredicate<JsonObject, JsonField> {

    private static final JsonFieldDefinition<String> JSON_STREAMING_TYPE =
            JsonFactory.newStringFieldDefinition("streamingType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<Boolean> JSON_SUBSCRIBED =
            JsonFactory.newBooleanFieldDefinition("subscribed", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final StreamingType streamingType;
    private final boolean subscribed;

    /**
     * Constructs a new acknowledge for when a subscription in the cluster was added/removed.
     *
     * @param streamingType the StreamingType of this StreamingAck message.
     * @param subscribed whether it is acknowledged if a subscription was added ({@link #subscribed} is {@code true}) or
     * removed ({@link #subscribed} is {@code false}).
     */
    public StreamingAck(final StreamingType streamingType, final boolean subscribed) {
        this.streamingType = streamingType;
        this.subscribed = subscribed;
    }

    /**
     * @return the StreamingType of this StreamingAck message.
     */
    public StreamingType getStreamingType() {
        return streamingType;
    }

    /**
     * @return whether it is acknowledged if a subscription was added ({@link #subscribed} is {@code true}) or removed
     * ({@link #subscribed} is {@code false}).
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Creates a new {@code StreamingAck} message from a JSON object.
     *
     * @param jsonObject the JSON object of which the StreamingAck is to be created.
     * @return the StreamingAck message.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static StreamingAck fromJson(final JsonObject jsonObject) {
        final String streamingType = jsonObject.getValueOrThrow(JSON_STREAMING_TYPE);
        final boolean subscribed = jsonObject.getValueOrThrow(JSON_SUBSCRIBED);

        return new StreamingAck(StreamingType.fromTopic(streamingType), subscribed);
    }

    @Override
    @Nonnull
    public JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    @Nonnull
    public JsonObject toJson(@Nonnull final JsonSchemaVersion schemaVersion,
            @Nonnull final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();
        jsonObjectBuilder.set(JSON_STREAMING_TYPE, streamingType.getDistributedPubSubTopic(), predicate);
        jsonObjectBuilder.set(JSON_SUBSCRIBED, subscribed, predicate);
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StreamingAck)) {
            return false;
        }
        final StreamingAck that = (StreamingAck) o;
        return subscribed == that.subscribed && streamingType == that.streamingType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamingType, subscribed);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "streamingType=" + streamingType +
                ", subscribed=" + subscribed +
                "]";
    }
}
