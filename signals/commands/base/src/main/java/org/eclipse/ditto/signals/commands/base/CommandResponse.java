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
package org.eclipse.ditto.signals.commands.base;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.base.Signal;

/**
 * Aggregates all possible responses relating to a given {@link Command}.
 *
 * @param <T> the type of the implementing class.
 */
public interface CommandResponse<T extends CommandResponse> extends Signal<T> {

    /**
     * Type qualifier of command responses.
     */
    String TYPE_QUALIFIER = "responses";

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

    @Override
    default JsonSchemaVersion getImplementedSchemaVersion() {
        return getDittoHeaders().getSchemaVersion().orElse(getLatestSchemaVersion());
    }

    /**
     * Returns the status code of the issued {@code CommandType}. The semantics of the codes is the one of HTTP Status
     * Codes (e.g.: {@literal 200} for "OK", {@literal 409} for "Conflict").
     *
     * @return the status code of the issued CommandType.
     */
    HttpStatusCode getStatusCode();

    /**
     * This convenience method returns the status code value of the issued {@link Command}. The semantics of the codes
     * is the one of HTTP Status Codes (e.g.: {@literal 200} for "OK", {@literal 409} for "Conflict").
     *
     * @return the status code value of the issued CommandType.
     * @see #getStatusCode()
     */
    default int getStatusCodeValue() {
        final HttpStatusCode statusCode = getStatusCode();
        return statusCode.toInt();
    }

    /**
     * Returns all non hidden marked fields of this command response.
     *
     * @return a JSON object representation of this command response including only regular, non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    JsonObject toJson(JsonSchemaVersion schemaVersion, Predicate<JsonField> predicate);

    /**
     * This class contains common definitions for all fields of a {@code CommandResponse}'s JSON representation.
     * Implementation of {@code CommandResponse} may add additional fields by extending this class.
     *
     */
    @Immutable
    abstract class JsonFields {

        /**
         * JSON field containing the response type as String.
         */
        public static final JsonFieldDefinition<String> TYPE = JsonFactory.newStringFieldDefinition("type",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message's status code as int.
         */
        public static final JsonFieldDefinition<Integer> STATUS = JsonFactory.newIntFieldDefinition("status",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * JSON field containing the message's payload as {@link JsonValue}.
         */
        public static final JsonFieldDefinition<JsonValue> PAYLOAD = JsonFactory.newJsonValueFieldDefinition("payload",
                FieldType.REGULAR, JsonSchemaVersion.V_1, JsonSchemaVersion.V_2);

        /**
         * Constructs a new {@code JsonFields} object.
         */
        protected JsonFields() {
            super();
        }

    }

}
