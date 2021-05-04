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
package org.eclipse.ditto.internal.utils.akka.streaming;

import java.util.Objects;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.Jsonifiable;

/**
 * Ack message that is used for acking elements while streaming.
 */
@Immutable
public final class StreamAck implements Jsonifiable<JsonValue> {

    /**
     * The element id.
     */
    private final String elementId;
    /**
     * The status of the ack.
     */
    private final Status status;

    /**
     * Create an ack.
     *
     * @param elementId the element for which the ack is sent.
     * @param status The status of the StreamAck.
     */
    private StreamAck(final String elementId, final Status status) {
        this.elementId = elementId;
        this.status = status;
    }

    /**
     * Get the element id.
     *
     * @return The element id.
     */
    String getElementId() {
        return elementId;
    }

    /**
     * Get the status.
     *
     * @return The status.
     */
    Status getStatus() {
        return status;
    }

    /**
     * Create an successful ack.
     *
     * @param elementId the element for which the ack is sent.
     * @return The StreamAck with status {@link Status#SUCCESS}.
     */
    public static StreamAck success(final String elementId) {
        return new StreamAck(elementId, Status.SUCCESS);
    }

    /**
     * Create an failure ack.
     *
     * @param elementId the element for which the ack is sent.}.
     * @return The StreamAck with status {@link Status#FAILURE}.
     */
    public static StreamAck failure(final String elementId) {
        return new StreamAck(elementId, Status.FAILURE);
    }

    /**
     * Create an ack.
     *
     * @param elementId the element for which the ack is sent.
     * @param status The status of the StreamAck.
     * @return The StreamAck.
     */
    private static StreamAck of(final String elementId, final Status status) {
        return new StreamAck(elementId, status);
    }

    /**
     * Status of ACKs.
     */
    public enum Status {
        /**
         * Success status.
         */
        SUCCESS,
        /**
         * Failure status.
         */
        FAILURE,
        /**
         * Status that can be used if status of ack is unknown.
         */
        UNKNOWN;

        /**
         * Create the status from its String representation.
         *
         * @param statusString The Status as String.
         * @return The Status instance or {@link #UNKNOWN} if unknown.
         */
        static Status fromString(final String statusString) {
            return Stream.of(values())
                    .filter(status -> status.name().equals(statusString))
                    .findFirst()
                    .orElse(Status.UNKNOWN);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonValue toJson() {
        return JsonFactory.newObjectBuilder()
                .set(JsonFields.ELEMENT_ID, elementId)
                .set(JsonFields.STATUS, status.name())
                .build();
    }

    /**
     * Creates a new {@code StreamAck} from a JSON object.
     *
     * @param jsonObject the JSON object of which a new StreamAck is to be created.
     * @return the StreamAck which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonObject} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} is not valid JSON.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the passed in {@code jsonObject} was not in the
     * expected 'StreamAck' format.
     */
    public static StreamAck fromJson(final JsonObject jsonObject) {
        final String extractedElementId = jsonObject.getValueOrThrow(StreamAck.JsonFields.ELEMENT_ID);
        final String extractedStatus = jsonObject.getValueOrThrow(JsonFields.STATUS);
        final Status status = Status.fromString(extractedStatus);
        return of(extractedElementId, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StreamAck streamAck = (StreamAck) o;
        return Objects.equals(elementId, streamAck.elementId) &&
                status == streamAck.status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(elementId, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "StreamAck{" +
                "elementId='" + elementId + '\'' +
                ", status=" + status +
                '}';
    }

    /**
     * An enumeration of the known {@link org.eclipse.ditto.json.JsonField}s of a StreamAck.
     */
    @Immutable
    private static final class JsonFields {

        /**
         * JSON field containing the StreamAcks's element Id.
         */
        private static final JsonFieldDefinition<String> ELEMENT_ID = JsonFactory.newStringFieldDefinition
                ("elementId");

        /**
         * JSON field containing the StreamAck's type.
         */
        private static final JsonFieldDefinition<String> STATUS = JsonFactory.newStringFieldDefinition("type");


        private JsonFields() {
            throw new AssertionError();
        }

    }
}
