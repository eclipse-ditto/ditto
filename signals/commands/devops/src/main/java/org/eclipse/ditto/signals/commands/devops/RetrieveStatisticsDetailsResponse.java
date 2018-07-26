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
 * Response to a {@link RetrieveStatisticsDetails} command containing a {@link JsonObject} of the retrieved
 * StatisticsDetails.
 */
@Immutable
public final class RetrieveStatisticsDetailsResponse
        extends AbstractDevOpsCommandResponse<RetrieveStatisticsDetailsResponse>
        implements WithEntity<RetrieveStatisticsDetailsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveStatisticsDetails.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_STATISTICS_DETAILS =
            JsonFactory.newJsonObjectFieldDefinition("statisticsDetails", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final JsonObject statisticsDetails;

    private RetrieveStatisticsDetailsResponse(final JsonObject statisticsDetails, final DittoHeaders dittoHeaders) {
        super(TYPE, null, null, HttpStatusCode.OK, dittoHeaders);
        this.statisticsDetails =
                Objects.requireNonNull(statisticsDetails, "The statisticsDetails JSON must not be null!");
    }

    /**
     * Returns a new instance of {@code RetrieveStatisticsDetailsResponse}.
     *
     * @param statistics the JSON representation of the retrieved Thing.
     * @param dittoHeaders the headers of the ThingCommand which caused this ThingCommandResponse.
     * @return a new statistics command response object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static RetrieveStatisticsDetailsResponse of(final JsonObject statistics, final DittoHeaders dittoHeaders) {
        return new RetrieveStatisticsDetailsResponse(statistics, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveStatisticsDetailsResponse} command from a JSON string.
     *
     * @param jsonString contains the data of the RetrieveStatisticsDetailsResponse command.
     * @param dittoHeaders the headers of the request.
     * @return the RetrieveStatisticsDetailsResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveStatisticsDetailsResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveStatisticsDetailsResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveStatisticsDetailsResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveStatisticsDetailsResponse>(TYPE, jsonObject)
                .deserialize(statusCode -> {
                    final JsonObject statistics = jsonObject.getValueOrThrow(JSON_STATISTICS_DETAILS);
                    return RetrieveStatisticsDetailsResponse.of(statistics, dittoHeaders);
                });
    }

    /**
     * Returns the JSON representation of the statistics.
     *
     * @return the JSON representation of the statistics.
     */
    public JsonObject getStatisticsDetails() {
        return statisticsDetails;
    }

    @Override
    public RetrieveStatisticsDetailsResponse setEntity(final JsonValue entity) {
        return of(entity.asObject(), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return statisticsDetails;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_STATISTICS_DETAILS, statisticsDetails, predicate);
    }

    @Override
    public RetrieveStatisticsDetailsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(statisticsDetails, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Objects.hashCode(statisticsDetails);
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
        final RetrieveStatisticsDetailsResponse that = (RetrieveStatisticsDetailsResponse) o;
        return that.canEqual(this) && Objects.equals(statisticsDetails, that.statisticsDetails) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveStatisticsDetailsResponse;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", statisticsDetails=" + statisticsDetails + "]";
    }

}
