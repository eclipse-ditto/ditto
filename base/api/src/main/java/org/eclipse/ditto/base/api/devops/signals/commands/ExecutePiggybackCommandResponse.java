/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to the {@link ExecutePiggybackCommand}.
 */
@Immutable
@JsonParsableCommandResponse(type = ExecutePiggybackCommandResponse.TYPE)
public final class ExecutePiggybackCommandResponse
        extends AbstractDevOpsCommandResponse<ExecutePiggybackCommandResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + ExecutePiggybackCommand.NAME;

    private static final JsonFieldDefinition<JsonValue> JSON_RESPONSE =
            JsonFieldDefinition.ofJsonValue("response", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final CommandResponseJsonDeserializer<ExecutePiggybackCommandResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();
                        return ExecutePiggybackCommandResponse.of(
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME).orElse(null),
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE).orElse(null),
                                context.getDeserializedHttpStatus(),
                                jsonObject.getValueOrThrow(JSON_RESPONSE),
                                context.getDittoHeaders()
                        );
                    });

    private final JsonValue response;

    private ExecutePiggybackCommandResponse(@Nullable final String serviceName,
            @Nullable final String instance,
            final HttpStatus status,
            final JsonValue response,
            final DittoHeaders dittoHeaders) {

        super(TYPE, serviceName, instance, status, dittoHeaders);
        this.response = response;
    }

    /**
     * Creates a new ExecutePiggybackCommandResponse instance.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance identifier of the serviceName from which the DevOpsCommandResponse originated.
     * @param status http status of the response.
     * @param response JSON value of the response.
     * @param dittoHeaders the DittoHeaders of the response.
     * @return the new instance.
     */
    public static ExecutePiggybackCommandResponse of(@Nullable final String serviceName,
            @Nullable final String instance,
            final HttpStatus status,
            final JsonValue response,
            final DittoHeaders dittoHeaders) {

        return new ExecutePiggybackCommandResponse(serviceName, instance, status, response, dittoHeaders);
    }

    /**
     * Creates a response to a ExecutePiggybackCommand from a JSON string.
     *
     * @param jsonString contains the data of the ExecutePiggybackCommandResponse.
     * @param dittoHeaders the headers of the request.
     * @return the ChangeLogLevelResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ExecutePiggybackCommandResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates an ExecutePiggybackCommandResponse from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the response.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ExecutePiggybackCommandResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {

        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    /**
     * Returns the original response as JSON.
     *
     * @return the original response.
     */
    public JsonValue getResponse() {
        return response;
    }

    @Override
    public ExecutePiggybackCommandResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getServiceName().orElse(null), getInstance().orElse(null), getHttpStatus(), response, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_RESPONSE, response, predicate);
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
        final var that = (ExecutePiggybackCommandResponse) o;
        return that.canEqual(this) && Objects.equals(response, that.response) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ExecutePiggybackCommandResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), response);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", successful=" + response + "]";
    }

}
