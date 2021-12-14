/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.devops.signals.commands;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link RetrieveStatisticsDetails} command containing a {@link org.eclipse.ditto.json.JsonObject} of the retrieved
 * StatisticsDetails.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveStatisticsDetailsResponse.TYPE)
public final class RetrieveStatisticsDetailsResponse
        extends AbstractDevOpsCommandResponse<RetrieveStatisticsDetailsResponse>
        implements WithEntity<RetrieveStatisticsDetailsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveStatisticsDetails.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_STATISTICS_DETAILS =
            JsonFieldDefinition.ofJsonObject("statisticsDetails", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveStatisticsDetailsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new RetrieveStatisticsDetailsResponse(
                                jsonObject.getValueOrThrow(JSON_STATISTICS_DETAILS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final JsonObject statisticsDetails;

    private RetrieveStatisticsDetailsResponse(final JsonObject statisticsDetails,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                null,
                null,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveStatisticsDetailsResponse.class),
                dittoHeaders);
        this.statisticsDetails = ConditionChecker.checkNotNull(statisticsDetails, "statisticsDetails");
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
        return new RetrieveStatisticsDetailsResponse(statistics, HTTP_STATUS, dittoHeaders);
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
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
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

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_STATISTICS_DETAILS, statisticsDetails, predicate);
    }

    @Override
    public RetrieveStatisticsDetailsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(statisticsDetails, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final var prime = 31;
        var result = super.hashCode();
        result = prime * result + Objects.hashCode(statisticsDetails);
        return result;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (RetrieveStatisticsDetailsResponse) o;
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
