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

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A {@link DevOpsCommandResponse} aggregating multiple {@link org.eclipse.ditto.base.model.signals.commands.CommandResponse}s.
 */
@Immutable
@JsonParsableCommandResponse(type = AggregatedDevOpsCommandResponse.TYPE)
public final class AggregatedDevOpsCommandResponse
        extends AbstractDevOpsCommandResponse<AggregatedDevOpsCommandResponse>
        implements WithEntity<AggregatedDevOpsCommandResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "aggregatedResponse";

    private static final JsonFieldDefinition<String> JSON_RESPONSES_TYPE =
            JsonFieldDefinition.ofString("responsesType", FieldType.REGULAR, JsonSchemaVersion.V_2);
    private static final JsonFieldDefinition<JsonObject> JSON_AGGREGATED_RESPONSES =
            JsonFieldDefinition.ofJsonObject("responses", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final CommandResponseJsonDeserializer<AggregatedDevOpsCommandResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return new AggregatedDevOpsCommandResponse(
                                jsonObject.getValueOrThrow(JSON_AGGREGATED_RESPONSES),
                                jsonObject.getValueOrThrow(JSON_RESPONSES_TYPE),
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders());
                    });

    private final JsonObject aggregatedResponses;
    private final String responsesType;

    private AggregatedDevOpsCommandResponse(final JsonObject aggregatedResponses,
            final String responsesType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE, null, null, httpStatus, dittoHeaders);
        this.aggregatedResponses = aggregatedResponses;
        this.responsesType = responsesType;
    }

    /**
     * Returns a new instance of {@code AggregatedDevOpsCommandResponse}.
     *
     * @param commandResponses the aggregated {@link DevOpsCommandResponse}s.
     * @param responsesType the responses type of the responses to expect.
     * @param httpStatus the HTTP status to send back as response status.
     * @param dittoHeaders the headers of the request.
     * @param aggregateResults true if a single response is required, false if aggregated multiple response are required
     * @return the new RetrieveLoggerConfigResponse response.
     * @since 2.0.0
     */
    public static AggregatedDevOpsCommandResponse of(final List<CommandResponse<?>> commandResponses,
            final String responsesType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders,
            final boolean aggregateResults) {

        final var jsonRepresentation = buildJsonRepresentation(commandResponses, dittoHeaders, aggregateResults);
        return new AggregatedDevOpsCommandResponse(jsonRepresentation, responsesType, httpStatus, dittoHeaders);
    }

    /**
     * Returns a new instance of {@code AggregatedDevOpsCommandResponse}.
     *
     * @param aggregatedResponses the aggregated {@link DevOpsCommandResponse}s as a JsonObject.
     * @param responsesType the responses type of the responses to expect.
     * @param httpStatus the HTTP status to send back as response status.
     * @param dittoHeaders the headers of the request.
     * @return the new RetrieveLoggerConfigResponse response.
     * @since 2.0.0
     */
    public static AggregatedDevOpsCommandResponse of(final JsonObject aggregatedResponses,
            final String responsesType,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        return new AggregatedDevOpsCommandResponse(aggregatedResponses, responsesType, httpStatus, dittoHeaders);
    }

    /**
     * Creates a response to a {@code AggregatedDevOpsCommandResponse} command from a JSON string.
     *
     * @param jsonString contains the data of the AggregatedDevOpsCommandResponse command.
     * @param dittoHeaders the headers of the request.
     * @return the AggregatedDevOpsCommandResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static AggregatedDevOpsCommandResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code AggregatedDevOpsCommandResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static AggregatedDevOpsCommandResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public AggregatedDevOpsCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(aggregatedResponses, responsesType, getHttpStatus(), dittoHeaders);
    }

    /**
     * @return the responses type of the responses to expect.
     */
    public String getResponsesType() {
        return responsesType;
    }

    @Override
    public AggregatedDevOpsCommandResponse setEntity(final JsonValue entity) {
        throw new UnsupportedOperationException("Setting entity on AggregatedDevOpsCommandResponse is not supported");
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return aggregatedResponses;
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_RESPONSES_TYPE, responsesType, predicate);
        jsonObjectBuilder.set(JSON_AGGREGATED_RESPONSES, aggregatedResponses, predicate);
    }

    private static JsonObject buildJsonRepresentation(final List<CommandResponse<?>> commandResponses,
            final DittoHeaders dittoHeaders,
            final boolean aggregateResults) {

        final var schemaVersion = dittoHeaders.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);

        if (!aggregateResults && commandResponses.size() == 1) {
            final CommandResponse<?> commandResponse = commandResponses.get(0);
            return commandResponseToJson(commandResponse, schemaVersion);
        } else {
            return buildAggregatedJsonRepresentation(commandResponses, schemaVersion);
        }
    }

    private static JsonObject buildAggregatedJsonRepresentation(final List<CommandResponse<?>> commandResponses,
            final JsonSchemaVersion schemaVersion) {
        final var builder = JsonObject.newBuilder();
        var i = 0;
        for (final var cmdR : commandResponses) {
            final var key = String.format("/%s/%s", calculateServiceName(cmdR), calculateInstance(cmdR, i++));
            // include both regular and special fields for devops command responses
            final JsonObject responseJson = commandResponseToJson(cmdR, schemaVersion);
            builder.set(key, responseJson);
        }
        if (builder.isEmpty()) {
            return JsonFactory.nullObject();
        } else {
            return builder.build();
        }
    }

    private static JsonObject commandResponseToJson(final CommandResponse<?> commandResponse,
            final JsonSchemaVersion schemaVersion) {
        final JsonObject responseJson;
        if (commandResponse instanceof ExecutePiggybackCommandResponse response) {
            responseJson = response.getResponse().asObject();
        } else {
            responseJson = commandResponse.toJson(schemaVersion, FieldType.regularOrSpecial());
        }
        return responseJson;
    }

    private static String calculateServiceName(final CommandResponse<?> commandResponse) {
        final String result;
        if (commandResponse instanceof DevOpsCommandResponse) {
            result = ((DevOpsCommandResponse<?>) commandResponse).getServiceName().orElse("?");
        } else {
            result = "?";
        }
        return result;
    }

    private static String calculateInstance(final CommandResponse<?> commandResponse, final int i) {
        final String result;
        final var fallBackValue = "?" + (i == 0 ? "" : String.valueOf(i));
        if (commandResponse instanceof DevOpsCommandResponse) {
            result = ((DevOpsCommandResponse<?>) commandResponse).getInstance()
                    .orElse(fallBackValue);
        } else {
            result = fallBackValue;
        }
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
        if (!super.equals(o)) {
            return false;
        }
        final var that = (AggregatedDevOpsCommandResponse) o;
        return that.canEqual(this) &&
                Objects.equals(responsesType, that.responsesType) &&
                Objects.equals(aggregatedResponses, that.aggregatedResponses) &&
                super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof AggregatedDevOpsCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), responsesType, aggregatedResponses);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", responsesType=" + responsesType +
                ", aggregatedResponses=" + aggregatedResponses + "]";
    }

}
