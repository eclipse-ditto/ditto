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

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.devops.ImmutableLoggerConfig;
import org.eclipse.ditto.model.devops.LoggerConfig;

/**
 * Command to change the LogLevel of a Service's Logger at runtime. The Logger to change can be specified through {@link
 * LoggerConfig}.
 */
@Immutable
public final class ChangeLogLevel extends AbstractDevOpsCommand<ChangeLogLevel> {

    /**
     * Name of the command.
     */
    public static final String NAME = "changeLogLevel";

    /**
     * Type of this command.
     */
    public static final String TYPE = TYPE_PREFIX + NAME;

    static final JsonFieldDefinition<JsonObject> JSON_LOGGER_CONFIG =
            JsonFactory.newJsonObjectFieldDefinition("loggerConfig", FieldType.REGULAR, JsonSchemaVersion.V_1,
                    JsonSchemaVersion.V_2);

    private final LoggerConfig loggerConfig;

    private ChangeLogLevel(final LoggerConfig loggerConfig, final DittoHeaders dittoHeaders) {
        super(TYPE, dittoHeaders);
        this.loggerConfig = requireNonNull(loggerConfig, "The logger configuration must not be null!");
    }

    /**
     * Returns a new instance of {@code ChangeLogLevel}.
     *
     * @param loggerConfig the configuration for the logger to change.
     * @param dittoHeaders the headers of the request.
     * @return a new ChangeLogLevel command.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ChangeLogLevel of(final LoggerConfig loggerConfig, final DittoHeaders dittoHeaders) {
        return new ChangeLogLevel(loggerConfig, dittoHeaders);
    }

    /**
     * Creates a new {@code ChangeLogLevel} from a JSON string.
     *
     * @param jsonString contains the data of the ChangeLogLevel command.
     * @param dittoHeaders the headers of the request.
     * @return the ChangeLogLevel command which is based on the data of {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonString} was not in the expected
     * format.
     */
    public static ChangeLogLevel fromJson(final String jsonString, final DittoHeaders dittoHeaders) {
        return fromJson(JsonFactory.newObject(jsonString), dittoHeaders);
    }

    /**
     * Creates a new {@code ChangeLogLevel} from a JSON object.
     *
     * @param jsonObject the JSON object of which the command is to be created.
     * @param dittoHeaders the headers of the command.
     * @return the command.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * format.
     */
    public static ChangeLogLevel fromJson(final JsonObject jsonObject, final DittoHeaders dittoHeaders) {
        return new DevOpsCommandJsonDeserializer<ChangeLogLevel>(TYPE, jsonObject).deserialize(() -> {
            final JsonObject loggerConfigJsonObject = jsonObject.getValueOrThrow(JSON_LOGGER_CONFIG);
            final LoggerConfig loggerConfig = ImmutableLoggerConfig.fromJson(loggerConfigJsonObject);

            return of(loggerConfig, dittoHeaders);
        });
    }

    /**
     * Returns the {@code LoggerConfig} to set.
     *
     * @return the LoggerConfig to set.
     */
    public LoggerConfig getLoggerConfig() {
        return loggerConfig;
    }

    @Override
    public ChangeLogLevel setDittoHeaders(final DittoHeaders dittoHeaders) {
        return of(loggerConfig, dittoHeaders);
    }

    @Override
    protected void appendPayload(final JsonObjectBuilder jsonObjectBuilder, final JsonSchemaVersion schemaVersion,
            final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        jsonObjectBuilder.set(JSON_LOGGER_CONFIG, loggerConfig.toJson(schemaVersion, thePredicate), predicate);
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
        final ChangeLogLevel that = (ChangeLogLevel) o;
        return that.canEqual(this) && Objects.equals(loggerConfig, that.loggerConfig) && super.equals(that);
    }

    @Override
    protected boolean canEqual(@Nullable final Object other) {
        return other instanceof ChangeLogLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), loggerConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + super.toString() + "loggerConfig=" + loggerConfig + "]";
    }

}
