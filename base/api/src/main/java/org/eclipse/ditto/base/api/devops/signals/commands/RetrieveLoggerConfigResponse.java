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
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.api.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.base.api.devops.LoggerConfig;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseHttpStatusValidator;
import org.eclipse.ditto.base.model.signals.commands.CommandResponseJsonDeserializer;
import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Response to the {@link RetrieveLoggerConfig} command.
 */
@Immutable
@JsonParsableCommandResponse(type = RetrieveLoggerConfigResponse.TYPE)
public final class RetrieveLoggerConfigResponse extends AbstractDevOpsCommandResponse<RetrieveLoggerConfigResponse>
        implements WithEntity<RetrieveLoggerConfigResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveLoggerConfig.NAME;

    public static final JsonFieldDefinition<JsonArray> JSON_LOGGER_CONFIGS =
            JsonFieldDefinition.ofJsonArray("loggerConfigs", FieldType.REGULAR, JsonSchemaVersion.V_2);

    private static final HttpStatus HTTP_STATUS = HttpStatus.OK;

    private static final CommandResponseJsonDeserializer<RetrieveLoggerConfigResponse> JSON_DESERIALIZER =
            CommandResponseJsonDeserializer.newInstance(TYPE,
                    context -> {
                        final var jsonObject = context.getJsonObject();

                        final var loggerConfigsJsonArray = jsonObject.getValueOrThrow(JSON_LOGGER_CONFIGS);
                        final var loggerConfigs = loggerConfigsJsonArray.stream()
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(ImmutableLoggerConfig::fromJson)
                                .toList();

                        return new RetrieveLoggerConfigResponse(
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME).orElse(null),
                                jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE).orElse(null),
                                loggerConfigs,
                                context.getDeserializedHttpStatus(),
                                context.getDittoHeaders()
                        );
                    });

    private final List<LoggerConfig> loggerConfigs;

    private RetrieveLoggerConfigResponse(@Nullable final String serviceName,
            @Nullable final String instance,
            final List<LoggerConfig> loggerConfigs,
            final HttpStatus httpStatus,
            final DittoHeaders dittoHeaders) {

        super(TYPE,
                serviceName,
                instance,
                CommandResponseHttpStatusValidator.validateHttpStatus(httpStatus,
                        Collections.singleton(HTTP_STATUS),
                        RetrieveLoggerConfigResponse.class),
                dittoHeaders);
        this.loggerConfigs = List.copyOf(loggerConfigs);
    }

    /**
     * Returns a new instance of {@code RetrieveLoggerConfigResponse}.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance identifier of the serviceName from which the DevOpsCommandResponse originated.
     * @param loggerConfigs the retrieved LoggerConfigs.
     * @param dittoHeaders the headers of the request.
     * @return the new RetrieveLoggerConfigResponse response.
     */
    public static RetrieveLoggerConfigResponse of(@Nullable final String serviceName,
            @Nullable final String instance,
            final List<LoggerConfig> loggerConfigs,
            final DittoHeaders dittoHeaders) {

        return new RetrieveLoggerConfigResponse(serviceName, instance, loggerConfigs, HTTP_STATUS, dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveLoggerConfigResponse} command from a JSON string.
     *
     * @param jsonString contains the data of the RetrieveLoggerConfigResponse command.
     * @param dittoHeaders the headers of the request.
     * @return the RetrieveLoggerConfigResponse command which is based on the dta of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static RetrieveLoggerConfigResponse fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonObject.of(jsonString), dittoHeaders);
    }

    /**
     * Creates a response to a {@code RetrieveLoggerConfigResponse} command from a JSON object.
     *
     * @param jsonObject the JSON object of which the response is to be created.
     * @param dittoHeaders the headers of the preceding command.
     * @return the response.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static RetrieveLoggerConfigResponse fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return JSON_DESERIALIZER.deserialize(jsonObject, dittoHeaders);
    }

    @Override
    public RetrieveLoggerConfigResponse setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(getServiceName().orElse(null), getInstance().orElse(null), loggerConfigs, dittoHeaders);
    }

    /**
     * Returns the retrieved {@code LoggerConfig}s.
     *
     * @return the retrieved LoggerConfigs.
     */
    public List<LoggerConfig> getLoggerConfigs() {
        return loggerConfigs;
    }

    @Override
    public RetrieveLoggerConfigResponse setEntity(final JsonValue entity) {
        final var entityJsonArray = entity.asArray();
        return of(getServiceName().orElse(null),
                getInstance().orElse(null),
                entityJsonArray.stream()
                        .filter(JsonValue::isObject)
                        .map(JsonValue::asObject)
                        .map(ImmutableLoggerConfig::fromJson)
                        .toList(),
                getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return loggerConfigs.stream()
                .map(LoggerConfig::toJson)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder,
            final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final var predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LOGGER_CONFIGS, getEntity(schemaVersion).asArray(), predicate);
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
        final var that = (RetrieveLoggerConfigResponse) o;
        return that.canEqual(this) && Objects.equals(loggerConfigs, that.loggerConfigs) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof RetrieveLoggerConfigResponse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), loggerConfigs);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + ", loggerConfigs=" + loggerConfigs + "]";
    }

}
