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
package org.eclipse.ditto.base.api.devops;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of the {@link LoggerConfig} interface.
 */
@Immutable
public final class ImmutableLoggerConfig implements LoggerConfig {

    private final LogLevel level;
    @Nullable private final String logger;

    private ImmutableLoggerConfig(final LogLevel level, @Nullable final String logger) {
        this.level = requireNonNull(level, "The LogLevel must not be null!");
        this.logger = logger;
    }

    /**
     * Returns a new instance of {@code LoggerConfig}.
     *
     * @param logLevel the level to set.
     * @return a new LoggerConfig.
     * @throws NullPointerException if {@code logLevel} is {@code null}.
     */
    public static LoggerConfig of(final LogLevel logLevel) {
        return of(logLevel, null);
    }

    /**
     * Returns a new instance of {@code LoggerConfig}.
     *
     * @param logLevel the level to set.
     * @param logger the logger to change.
     * @return a new LoggerConfig.
     * @throws NullPointerException if {@code logLevel} is {@code null}.
     */
    public static LoggerConfig of(final LogLevel logLevel, @Nullable final String logger) {
        return new ImmutableLoggerConfig(logLevel, logger);
    }

    /**
     * Creates a new {@code LoggerConfig} object from the specified JSON string.
     *
     * @param jsonString a JSON string which provides the data for the LoggerConfig to be created.
     * @return a new LoggerConfig which is initialised with the extracted data from {@code jsonString}.
     * @throws NullPointerException if {@code jsonString} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonString} does not contain all required fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonString} contains invalid JSON.
     */
    public static LoggerConfig fromJson(final String jsonString) {
        return fromJson(JsonFactory.newObject(jsonString));
    }

    /**
     * Creates a new {@code LoggerConfig} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the LoggerConfig to be created.
     * @return a new LoggerConfig which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if {@code jsonObject} does not contain all required fields.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contains invalid JSON.
     */
    public static LoggerConfig fromJson(final JsonObject jsonObject) {
        final LogLevel level = LogLevel.forIdentifier(jsonObject.getValueOrThrow(JsonFields.LEVEL))
                .orElseThrow(() -> new JsonParseException("Invalid LogLevel!"));

        final Optional<String> logger = jsonObject.getValue(JsonFields.LOGGER);

        return logger.map(s -> of(level, s)).orElseGet(() -> of(level));
    }

    @Override
    public LogLevel getLevel() {
        return level;
    }

    @Override
    public Optional<String> getLogger() {
        return Optional.ofNullable(logger);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        jsonObjectBuilder.set(JsonFields.LEVEL, level.getIdentifier(), predicate);

        getLogger().ifPresent(l -> jsonObjectBuilder.set(JsonFields.LOGGER, l, predicate));

        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLoggerConfig that = (ImmutableLoggerConfig) o;
        return level == that.level && Objects.equals(logger, that.logger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, logger);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "level=" + level + ", logger=" + logger + "]";
    }
}
