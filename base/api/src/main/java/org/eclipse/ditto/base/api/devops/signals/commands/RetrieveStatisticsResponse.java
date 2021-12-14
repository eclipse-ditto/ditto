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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to a {@link RetrieveStatistics} command containing a {@link org.eclipse.ditto.json.JsonObject} of the retrieved Statistics.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveStatisticsResponse.TYPE)
public final class RetrieveStatisticsResponse extends AbstractDevOpsCommandResponse<RetrieveStatisticsResponse>
        implements WithEntity<RetrieveStatisticsResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveStatistics.NAME;

    private static final JsonFieldDefinition<JsonObject> JSON_STATISTICS =
            JsonFieldDefinition.ofJsonObject("statistics", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveStatisticsResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new RetrieveStatisticsResponse(jsonObject.getValueOrThrow(JSON_STATISTICS),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final JsonObject statistics;

    private RetrieveStatisticsResponse(final JsonObject statistics,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                null,
                null,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveStatisticsResponse.class),
                dittoHeaders);
        this.statistics = ConditionChecker.checkNotNull(statistics, "statistics");
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
        return new RetrieveStatisticsResponse(statistics, HTTP_STATUS, dittoHeaders);
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
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
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
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_STATISTICS, statistics, predicate);
    }

    @Override
    public RetrieveStatisticsResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(statistics, dittoHeaders);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        final var prime = 31;
        var result = super.hashCode();
        result = prime * result + Objects.hashCode(statistics);
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
        final var that = (RetrieveStatisticsResponse) o;
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
