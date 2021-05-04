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
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Command to modify an actor's config.
 */
@JsonParsableCommand(typePrefix = CommonCommand.TYPE_PREFIX, name = ModifyConfig.NAME)
public final class ModifyConfig extends CommonCommand<ModifyConfig> {

    /**
     * Name of this command.
     */
    public static final String NAME = "modifyConfig";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_CONFIG =
            JsonFactory.newJsonObjectFieldDefinition("config");

    private final JsonObject config;

    private ModifyConfig(final JsonObject config, final DittoHeaders dittoHeaders) {
        super(TYPE, Category.MODIFY, dittoHeaders);
        this.config = config;
    }

    /**
     * Create a {@code ModifyConfig}.
     *
     * @param config pre-rendered config object.
     * @param headers Ditto headers.
     * @return the {@code ModifyConfig}.
     */
    public static ModifyConfig of(final JsonObject config, final DittoHeaders headers) {
        return new ModifyConfig(config, headers);
    }

    /**
     * Creates a new {@code ModifyConfig} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if the JSON object does not contain the field "config".
     */
    public static ModifyConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new ModifyConfig(jsonObject.getValueOrThrow(JSON_CONFIG), dittoHeaders);
    }

    /**
     * Return the config.
     *
     * @return the config.
     */
    public JsonObject getConfig() {
        return config;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(JSON_CONFIG, config);
    }

    @Override
    public ModifyConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new ModifyConfig(config, dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        if (super.equals(that) && that instanceof ModifyConfig) {
            return Objects.equals(config, ((ModifyConfig) that).config);
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
