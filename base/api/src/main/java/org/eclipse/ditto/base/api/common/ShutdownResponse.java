/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.common;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Response to {@code Shutdown} containing the retrieved config.
 */
@JsonParsableCommandResponse(type = ShutdownResponse.TYPE)
public final class ShutdownResponse extends CommonCommandResponse<ShutdownResponse> {

    /**
     * Type of this command response.
     */
    public static final String TYPE = TYPE_PREFIX + Shutdown.NAME;

    private static final JsonFieldDefinition<JsonValue> JSON_MESSAGE =
            JsonFactory.newJsonValueFieldDefinition("message");

    private final JsonValue message;

    private ShutdownResponse(final JsonValue message, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.message = message;
    }

    /**
     * Create a {@code ShutdownResponse}.
     *
     * @param message what to say in the response.
     * @param headers Ditto headers.
     * @return the {@code ShutdownResponse}.
     */
    public static ShutdownResponse of(final Object message, final DittoHeaders headers) {
        return new ShutdownResponse(JsonValue.of(message), headers);
    }

    /**
     * Creates a new {@code ShutdownResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object does not contain the field "config".
     */
    public static ShutdownResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new ShutdownResponse(jsonObject.getValueOrThrow(JSON_MESSAGE), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JSON_MESSAGE, message);
    }

    @Override
    public ShutdownResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ShutdownResponse(message, dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        if (super.equals(that) && that instanceof ShutdownResponse) {
            return Objects.equals(message, ((ShutdownResponse) that).message);
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), message);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                super.toString() +
                ", message=" + message +
                "]";
    }
}
