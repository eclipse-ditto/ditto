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
 * Response to {@code RetrieveConfig} containing the retrieved config.
 */
@JsonParsableCommandResponse(type = RetrieveConfigResponse.TYPE)
public final class RetrieveConfigResponse extends CommonCommandResponse<RetrieveConfigResponse> {

    /**
     * Type of this command response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveConfig.NAME;

    private static final JsonFieldDefinition<JsonValue> JSON_CONFIG = JsonFactory.newJsonValueFieldDefinition("config");

    private final JsonValue config;

    private RetrieveConfigResponse(final JsonValue config, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatus.OK, dittoHeaders);
        this.config = config;
    }

    /**
     * Create a {@code RetrieveConfigResponse}.
     *
     * @param config pre-rendered config object.
     * @param headers Ditto headers.
     * @return the {@code RetrieveConfigResponse}.
     */
    public static RetrieveConfigResponse of(final JsonValue config, final DittoHeaders headers) {
        return new RetrieveConfigResponse(config, headers);
    }

    /**
     * Creates a new {@code RetrieveConfigResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object does not contain the field "config".
     */
    public static RetrieveConfigResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new RetrieveConfigResponse(jsonObject.getValueOrThrow(JSON_CONFIG), dittoHeaders);
    }

    /**
     * Returns the retrieved config.
     *
     * @return the config.
     * @since 2.1.0
     */
    public JsonValue getConfig() {
        return config;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JSON_CONFIG, config);
    }

    @Override
    public RetrieveConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveConfigResponse(config, dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        if (super.equals(that) && that instanceof RetrieveConfigResponse) {
            return Objects.equals(config, ((RetrieveConfigResponse) that).config);
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", config=" + config +
                "]";
    }
}
