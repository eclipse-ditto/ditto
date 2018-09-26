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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;

/**
 * Response to a {@link RetrieveStatistics} command containing a {@link JsonObject} of the retrieved Statistics.
 */
@Immutable
public final class RetrieveStatisticsResponse extends AbstractDevOpsCommandResponse<RetrieveStatisticsResponse>
        implements WithEntity<RetrieveStatisticsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveStatistics.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_STATISTICS =
            JsonFactory.newJsonObjectFieldDefinition("statistics", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final JsonObject statistics;

    private RetrieveStatisticsResponse(final JsonObject statistics, final DittoHeaders dittoHeaders) {
        super(TYPE, null, null, HttpStatusCode.OK, dittoHeaders);
        this.statistics = Objects.requireNonNull(statistics, "The statistics JSON must not be null!");
    }

    /**
     * Returns a new instance of {@code RetrieveStatisticsResponse}.
     *
     * @param statistics the JSON representation of the retrieved Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused this ThingCommandResponse.
     * @return a new statistics command response object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveStatisticsResponse of(final JsonObject statistics, final DittoHeaders dittoHeaders) {
        return new RetrieveStatisticsResponse(statistics, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveStatisticsResponse} command from a JSON string.
     *
     * @param jsonString contains the data of the RetrieveStatisticsResponse command.
     * @param dittoHeaders the headers of the request.
     * @return the RetrieveStatisticsResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveStatisticsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveStatisticsResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveStatisticsResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveStatisticsResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final JsonObject statistics = jsonObject.getValueOrThrow(JSON_STATISTICS);
                    return RetrieveStatisticsResponse.of(statistics, dittoHeaders);
                });
    }

    /**
     * Returns the JSON representation of the statistics.
     *
     * @return the JSON representation of the statistics.
     */
    public JsonObject getStatistics() {
        return statistics;
    }

    @Override
    public RetrieveStatisticsResponse setEntity(final JsonValue entity) {
        return of(entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return statistics;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_STATISTICS, statistics, predicate);
    }

    @Override
    public RetrieveStatisticsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(statistics, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(statistics);
        return result;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RetrieveStatisticsResponse that = (RetrieveStatisticsResponse) o;
        return that.canEqual(this) && Objects.equals(statistics, that.statistics) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveStatisticsResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", statistics=" + statistics + "]";
    }

}
