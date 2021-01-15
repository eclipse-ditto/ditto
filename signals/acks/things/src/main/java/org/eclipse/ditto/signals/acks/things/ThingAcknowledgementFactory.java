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
package org.eclipse.ditto.signals.acks.things;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatus;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.base.AcknowledgementJsonParser;

/**
 * This class provides factory methods for getting instances of {@link Acknowledgement} in the context of Thing entity.
 *
 * @since 1.1.0
 */
@Immutable
public final class ThingAcknowledgementFactory {

    private ThingAcknowledgementFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @deprecated as of 2.0.0 please use
     * {@link #newAcknowledgement(AcknowledgementLabel, ThingId, HttpStatus, DittoHeaders, JsonValue)} instead.
     */
    @Deprecated
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return newAcknowledgement(label, thingId, statusCode.getAsHttpStatus(), dittoHeaders, payload);
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @param payload the optional payload of the Acknowledgement.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @since 2.0.0
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            @Nullable final JsonValue payload) {

        return Acknowledgement.of(label, thingId, httpStatus, dittoHeaders, payload);
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param statusCode the status code (HTTP semantics) of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @deprecated as of 2.0.0 please us
     * {@link #newAcknowledgement(AcknowledgementLabel, ThingId, HttpStatus, DittoHeaders)} instead.
     */
    @Deprecated
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatusCode statusCode,
            final DittoHeaders dittoHeaders) {

        return newAcknowledgement(label, thingId, statusCode.getAsHttpStatus(), dittoHeaders);
    }

    /**
     * Returns a new {@link Acknowledgement} instance.
     *
     * @param label the label of the new Acknowledgement.
     * @param thingId the ID of the affected Thing being acknowledged.
     * @param httpStatus the HTTP status of the Acknowledgement.
     * @param dittoHeaders the DittoHeaders.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code thingId} is empty.
     * @since 2.0.0
     */
    public static Acknowledgement newAcknowledgement(final AcknowledgementLabel label,
            final ThingId thingId,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return newAcknowledgement(label, thingId, httpStatus, dittoHeaders, null);
    }

    /**
     * Returns a JsonParser instance.
     *
     * @return the instance.
     */
    public static JsonParser getJsonParser() {
        return new JsonParser();
    }

    /**
     * Parses the given JSON object to an {@code Acknowledgement}.
     *
     * @param jsonObject the JSON object to be parsed.
     * @return the Acknowledgement.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} misses a required field.
     * @throws JsonParseException if {@code jsonObject} contained an unexpected value.
     */
    public static Acknowledgement fromJson(final JsonObject jsonObject) {
        final JsonParser jsonParser = getJsonParser();
        return jsonParser.apply(jsonObject);
    }

    /**
     * Parses an Acknowledgement with a ThingId from a {@link JsonObject}.
     */
    @Immutable
    static final class JsonParser extends AcknowledgementJsonParser<ThingId> {

        private JsonParser() {
            super();
        }

        @Override
        public ThingId createEntityIdInstance(final CharSequence entityIdValue) {
            return ThingId.of(entityIdValue);
        }

    }

}
