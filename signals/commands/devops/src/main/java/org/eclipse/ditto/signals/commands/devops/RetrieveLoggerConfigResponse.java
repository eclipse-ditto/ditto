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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
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
import org.eclipse.ditto.model.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.model.devops.LoggerConfig;
import org.eclipse.ditto.signals.commands.base.CommandResponseJsonDeserializer;
import org.eclipse.ditto.signals.commands.base.WithEntity;

/**
 * Response to the {@link RetrieveLoggerConfig} command.
 */
@Immutable
public final class RetrieveLoggerConfigResponse extends AbstractDevOpsCommandResponse<RetrieveLoggerConfigResponse>
    implements WithEntity<RetrieveLoggerConfigResponse> {

    /**
     * Type of this response.
     */
    public static final String TYPE = TYPE_PREFIX + RetrieveLoggerConfig.NAME;

    public static final JsonFieldDefinition<JsonArray> JSON_LOGGER_CONFIGS =
            JsonFactory.newJsonArrayFieldDefinition("loggerConfigs", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final List<LoggerConfig> loggerConfigs;

    private RetrieveLoggerConfigResponse(@Nullable final String serviceName, @Nullable final Integer instance,
            final List<LoggerConfig> loggerConfigs, final DittoHeaders dittoHeaders) {
        super(TYPE, serviceName, instance, HttpStatusCode.OK, dittoHeaders);
        this.loggerConfigs = Collections.unmodifiableList(new ArrayList<>(loggerConfigs));
    }

    /**
     * Returns a new instance of {@code RetrieveLoggerConfigResponse}.
     *
     * @param serviceName the service name from which the DevOpsCommandResponse originated.
     * @param instance the instance index of the serviceName from which the DevOpsCommandResponse originated.
     * @param loggerConfigs the retrieved LoggerConfigs.
     * @param dittoHeaders the headers of the request.
     * @return the new RetrieveLoggerConfigResponse response.
     */
    public static RetrieveLoggerConfigResponse of(@Nullable final String serviceName, @Nullable final Integer instance,
            final List<LoggerConfig> loggerConfigs,
            final DittoHeaders dittoHeaders) {
        return new RetrieveLoggerConfigResponse(serviceName, instance, loggerConfigs, dittoHeaders);
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
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
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
    public static RetrieveLoggerConfigResponse fromJson(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        return new CommandResponseJsonDeserializer<RetrieveLoggerConfigResponse>(TYPE, jsonObject)
                .deserialize((statusCode) -> {
                    final String serviceName = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_SERVICE_NAME)
                            .orElse(null);
                    final Integer instance = jsonObject.getValue(DevOpsCommandResponse.JsonFields.JSON_INSTANCE)
                            .orElse(null);
                    final JsonArray loggerConfigsJsonArray = jsonObject.getValueOrThrow(JSON_LOGGER_CONFIGS);
                    final List<LoggerConfig> loggerConfigs = loggerConfigsJsonArray.stream()
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .map(ImmutableLoggerConfig::fromJson)
                            .collect(Collectors.toList());

                    return of(serviceName, instance, loggerConfigs, dittoHeaders);
                });
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
        return of(getServiceName().orElse(null), getInstance().orElse(null), entity.asArray().stream()
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(ImmutableLoggerConfig::fromJson)
                .collect(Collectors.toList()), getDittoHeaders());
    }

    @Override
    public JsonValue getEntity(final JsonSchemaVersion schemaVersion) {
        return loggerConfigs.stream()
                .map(LoggerConfig::toJson)
                .collect(JsonCollectors.valuesToArray());
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {

        super.appendPayload(jsonObjectBuilder, schemaVersion, thePredicate);

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LOGGER_CONFIGS, loggerConfigs.stream()
                .map(LoggerConfig::toJson)
                .collect(JsonCollectors.valuesToArray()), predicate)
                .build();
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
        final RetrieveLoggerConfigResponse that = (RetrieveLoggerConfigResponse) o;
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
