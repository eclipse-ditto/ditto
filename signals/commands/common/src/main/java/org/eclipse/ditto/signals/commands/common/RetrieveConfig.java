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

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Command to retrieve a config object from an actor.
 */
@Immutable
@JsonParsableCommand(typePrefix = RetrieveConfig.TYPE_PREFIX, name = RetrieveConfig.NAME)
public final class RetrieveConfig extends CommonCommand<RetrieveConfig> {

    /**
     * The name of the command.
     */
    static final String NAME = "retrieveConfig";

    /**
     * The type of the command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    public RetrieveConfig of() {
        return new RetrieveConfig(DittoHeaders.empty());
    }

    private RetrieveConfig(final DittoHeaders dittoHeaders) {
        super(TYPE, Category.QUERY, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> predicate) {
        // this command has no payload.
    }

    @Override
    public RetrieveConfig setDittoHeaders(final DittoHeaders dittoHeaders) {
        return new RetrieveConfig(dittoHeaders);
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
        // jsonObject is ignored because this command has no payload.
        return new RetrieveConfig(dittoHeaders);
    }
}
