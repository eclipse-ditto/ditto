/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.devops;

import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandJsonDeserializer;

/**
 * Command which retrieves publicly available statistics about the stored Things currently present.
 */
@Immutable
public final class RetrieveStatistics extends AbstractDevOpsCommand<RetrieveStatistics> {

    /**
     * Name of the command.
     */
    public static final String NAME = "retrieveStatistics";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    private RetrieveStatistics(final DittoHeaders dittoHeaders) {
        super(TYPE, null, null, dittoHeaders);
    }

    /**
     * Returns a Command for retrieving statistics.
     *
     * @param dittoHeaders the optional command headers of the request.
     * @return a Command for retrieving statistics.
     */
    public static RetrieveStatistics of(final DittoHeaders dittoHeaders) {
        return new RetrieveStatistics(dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveStatistics} from a JSON string.
     *
     * @param jsonString contains the data of the RetrieveStatistics command.
     * @param dittoHeaders the headers of the request.
     * @return the RetrieveStatistics command which is based on the data of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveStatistics fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code RetrieveStatistics} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveStatistics fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandJsonDeserializer<RetrieveStatistics>(TYPE, jsonObject)
                .deserialize(() -> RetrieveStatistics.of(dittoHeaders));
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        jsonObjectBuilder.build();
    }

    @Override
    public Category getCategory() {
        return Category.QUERY;
    }

    @Override
    public RetrieveStatistics setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(dittoHeaders);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "]";
    }

}
