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
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Command to retrieve a config object from an actor.
 */
@Immutable
@JsonParsableCommand(typePrefix = CommonCommand.TYPE_PREFIX, name = RetrieveConfig.NAME)
public final class RetrieveConfig extends CommonCommand<RetrieveConfig> {

    /**
     * The name of the command.
     */
    static final String NAME = "retrieveConfig";

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private static final JsonFieldDefinition<String> JSON_PATH = JsonFactory.newStringFieldDefinition("path");

    @Nullable
    private final String path;

    private RetrieveConfig(@Nullable final String path, final DittoHeaders dittoHeaders) {
        super(TYPE, Category.QUERY, dittoHeaders);
        this.path = path;
    }

    /**
     * Create a RetrieveConfig object with empty path and empty headers.
     *
     * @return a RetrieveConfig object.
     */
    public static RetrieveConfig of() {
        return of(null, DittoHeaders.empty());
    }

    /**
     * Create a RetrieveConfig object with the given path and empty headers.
     *
     * @param path the path to select, or null to select everything.
     * @param dittoHeaders the Ditto headers.
     * @return the RetrieveConfig object.
     */
    public static RetrieveConfig of(@Nullable final String path, final DittoHeaders dittoHeaders) {
        return new RetrieveConfig(path, dittoHeaders);
    }

    /**
     * Return the path if present.
     *
     * @return the path, or an empty optional.
     */
    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {

        if (path != null) {
            jsonObjectBuilder.set(JSON_PATH, path);
        }
    }

    @Override
    public RetrieveConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveConfig(path, dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveConfig} from the given JSON object.
     *
     * @param jsonObject the JSON object of which the Shutdown is to be created.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static RetrieveConfig fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        final String path = jsonObject.getValue(JSON_PATH).orElse(null);
        return new RetrieveConfig(path, dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        if (super.equals(that) && that instanceof RetrieveConfig) {
            return Objects.equals(path, ((RetrieveConfig) that).path);
        } else {
            return false;
        }

    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), path);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() +
                ", path=" + path +
                "]";
    }
}
