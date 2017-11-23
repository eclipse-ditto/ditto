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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.WithEntity;

/**
 * A {@link DevOpsCommandResponse} aggregating multiple {@link DevOpsCommandResponse}s.
 */
@Immutable
public final class AggregatedDevOpsCommandResponse
        extends AbstractDevOpsCommandResponse<AggregatedDevOpsCommandResponse>
        implements WithEntity<AggregatedDevOpsCommandResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + "aggregatedResponse";

    static final JsonFieldDefinition<String> JSON_RESPONSES_TYPE =
            JsonFactory.newStringFieldDefinition("responsesType", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);
    static final JsonFieldDefinition<JsonObject> JSON_AGGREGATED_RESPONSES =
            JsonFactory.newJsonObjectFieldDefinition("responses", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final List<DevOpsCommandResponse<?>> aggregatedResponses;
    private final String responsesType;

    private AggregatedDevOpsCommandResponse(final List<DevOpsCommandResponse<?>> aggregatedResponses,
            final String responsesType, final DittoHeaders dittoHeaders) {
        super(TYPE, null, null, HttpStatusCode.OK, dittoHeaders);
        this.aggregatedResponses = Collections.unmodifiableList(new ArrayList<>(aggregatedResponses));
        this.responsesType = responsesType;
    }

    /**
     * Returns a new instance of {@code AggregatedDevOpsCommandResponse}.
     *
     * @param devOpsCommandResponses the aggregated {@link DevOpsCommandResponse}s.
     * @param responsesType the responses type of the responses to expect.
     * @param dittoHeaders the headers of the request.
     * @return the new RetrieveLoggerConfigResponse response.
     */
    public static AggregatedDevOpsCommandResponse of(final List<DevOpsCommandResponse<?>> devOpsCommandResponses,
            final String responsesType, final DittoHeaders dittoHeaders) {
        return new AggregatedDevOpsCommandResponse(devOpsCommandResponses, responsesType, dittoHeaders);
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
    public static AggregatedDevOpsCommandResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders,
            final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders, parseStrategies);
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
            final DittoHeaders dittoHeaders, final Map<String, JsonParsable<DevOpsCommandResponse>> parseStrategies) {
        return new DevOpsCommandResponseJsonDeserializer<AggregatedDevOpsCommandResponse>(TYPE, jsonObject)
                .deserialize(() -> {
                    final String theResponsesType = jsonObject.getValueOrThrow(JSON_RESPONSES_TYPE);
                    final JsonObject aggregatedResponsesJsonObj = jsonObject.getValueOrThrow(JSON_AGGREGATED_RESPONSES);
                    final List<DevOpsCommandResponse<?>> theDevOpsCommandsResponses =
                            aggregatedResponsesJsonObj.stream()
                                    .flatMap(serviceField -> {
                                        final JsonValue serviceInstances = serviceField.getValue();
                                        return serviceInstances.asObject().stream()
                                                .<DevOpsCommandResponse<?>>map(instanceField -> {
                                                    final JsonValue commandResponse = instanceField.getValue();

                                                    return parseStrategies.get(theResponsesType)
                                                            .<DevOpsCommandResponse<?>>parse(commandResponse.asObject(),
                                                                    dittoHeaders);
                                                });
                                    }).collect(Collectors.toList());

                    return of(theDevOpsCommandsResponses, theResponsesType, dittoHeaders);
                });
    }

    @Override
    public AggregatedDevOpsCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(aggregatedResponses, responsesType, dittoHeaders);
    }

    /**
     * @return the responses type of the responses to expect.
     */
    public String getResponsesType() {
        return responsesType;
    }

    /**
     * @return the aggregated {@link DevOpsCommandResponse}s.
     */
    public List<DevOpsCommandResponse<?>> getAggregatedResponses() {
        return aggregatedResponses;
    }

    @Override
    public AggregatedDevOpsCommandResponse setEntity(final JsonValue entity) {
        throw new UnsupportedOperationException("Setting entity on AggregatedDevOpsCommandResponse is not supported");
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return buildJsonRepresentation(schemaVersion);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_RESPONSES_TYPE, responsesType, predicate);
        jsonObjectBuilder.set(JSON_AGGREGATED_RESPONSES, buildJsonRepresentation(predicate));
    }

    private JsonObject buildJsonRepresentation(final Predicate<JsonField> predicate) {
        final JsonObjectBuilder builder = JsonObject.newBuilder();

        aggregatedResponses.forEach(cmdR ->
                builder.set("/" + cmdR.getServiceName().orElse("empty") + "/" +
                                String.valueOf(cmdR.getInstance().orElse(-1)),
                        cmdR.toJson(predicate)));

        return builder.build();
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
        final AggregatedDevOpsCommandResponse that = (AggregatedDevOpsCommandResponse) o;
        return that.canEqual(this) && Objects.equals(responsesType, that.responsesType) &&
                Objects.equals(aggregatedResponses, that.aggregatedResponses) && super.equals(that);
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
