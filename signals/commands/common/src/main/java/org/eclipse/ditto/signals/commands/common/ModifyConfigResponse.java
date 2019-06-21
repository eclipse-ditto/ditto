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
package org.eclipse.ditto.signals.commands.common;

import java.util.Objects;
import java.util.function.Predicate;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Response to {@code ModifyConfig} containing the retrieved config.
 */
@JsonParsableCommandResponse(type = ModifyConfigResponse.TYPE)
public final class ModifyConfigResponse extends CommonCommandResponse<ModifyConfigResponse> {

    /**
     * Type of this command response.
     */
    public static final String TYPE = TYPE_PREFIX + ModifyConfig.NAME;

    private static final JsonFieldDefinition<JsonObject> CONFIG = JsonFactory.newJsonObjectFieldDefinition("config");

    private final JsonObject config;

    private ModifyConfigResponse(final JsonObject config, final DittoHeaders dittoHeaders) {
        super(TYPE, HttpStatusCode.OK, dittoHeaders);
        this.config = config;
    }

    /**
     * Create a {@code ModifyConfigResponse}.
     *
     * @param config pre-rendered config object.
     * @param headers Ditto headers.
     * @return the {@code ModifyConfigResponse}.
     */
    public static ModifyConfigResponse of(final JsonObject config, final DittoHeaders headers) {
        return new ModifyConfigResponse(config, headers);
    }

    /**
     * Creates a new {@code ModifyConfigResponse} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object does not contain the field "config".
     */
    public static ModifyConfigResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new ModifyConfigResponse(jsonObject.getValueOrThrow(CONFIG), dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(CONFIG, config);
    }

    @Override
    public ModifyConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyConfigResponse(config, dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        if (super.equals(that) && that instanceof ModifyConfigResponse) {
            return Objects.equals(config, ((ModifyConfigResponse) that).config);
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), config);
    }
}
