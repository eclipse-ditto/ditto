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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.common.CommonCommand;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;

/**
 * Command to retrieve information about the current user.
 * @since 1.2.0
 */
@Immutable
@JsonParsableCommand(typePrefix = Whoami.TYPE_PREFIX, name = Whoami.NAME)
public final class Whoami extends CommonCommand<Whoami> {
    static final String TYPE_PREFIX = CommonCommand.TYPE_PREFIX;

    /**
     * The name of the command.
     */
    static final String NAME = "whoami";

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + Whoami.NAME;

    /**
     * Constructs a new {@code Whoami} object.
     *
     * @param dittoHeaders the headers of the command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    private Whoami(final DittoHeaders dittoHeaders) {
        super(TYPE, Category.QUERY, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // intentionally empty as no additional information is needed.
    }

    /**
     * Create a new Whoami command.
     * @param headers the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code headers} is {@code null}.
     */
    public static Whoami of(final DittoHeaders headers) {
        return new Whoami(headers);
    }

    /**
     * Creates a new {@code Whoami} from the given JSON object.
     *
     * @param jsonObject the JSON object.
     * @param dittoHeaders the headers.
     * @return the command.
     * @throws NullPointerException if {@code dittoHeaders} is {@code null}.
     */
    // intentionally has {@code jsonObject} as it wouldn't be parsable as {@link JsonParsableCommand} otherwise
    public static Whoami fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new Whoami(dittoHeaders);
    }

    @Override
    public Whoami setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new Whoami(dittoHeaders);
    }

    @Override
    public boolean equals(final Object that) {
        return super.equals(that) && that instanceof Whoami;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

    @Override
    @Nonnull
    public JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }
}
