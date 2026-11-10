/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.iotdb;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;

/**
 * Maps JSON scalars to the typed IoTDB columns and back. Numbers are stored as DOUBLE, so an integral value
 * within the exact double range is read back as an integer.
 */
@Immutable
final class IoTDbValues {

    private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_992d;

    private IoTDbValues() {
        throw new AssertionError();
    }

    /**
     * A JSON scalar bound to the column type that stores it.
     *
     * @param type the column type.
     * @param value a {@code String}, {@code Double} or {@code Boolean} matching {@code type}.
     */
    record Typed(TagType type, Object value) {}

    /**
     * @param value a JSON value.
     * @return the typed value, or empty for JSON {@code null}.
     * @throws IllegalArgumentException if {@code value} is an object or array.
     */
    static Optional<Typed> typed(final JsonValue value) {
        if (value.isNull()) {
            return Optional.empty();
        }
        if (value.isBoolean()) {
            return Optional.of(new Typed(TagType.BOOLEAN, value.asBoolean()));
        }
        if (value.isNumber()) {
            return Optional.of(new Typed(TagType.NUMBER, value.asDouble()));
        }
        if (value.isString()) {
            return Optional.of(new Typed(TagType.STRING, value.asString()));
        }
        throw new IllegalArgumentException(
                "Cannot store timeseries value of complex JSON type (object or array): " + value +
                        ". Only number, string, boolean and null values are supported.");
    }

    /**
     * @param number a stored DOUBLE.
     * @return the JSON number, integral if the double holds an exact integer.
     */
    static JsonValue number(final double number) {
        if (number == Math.rint(number) && Math.abs(number) <= MAX_EXACT_INTEGER) {
            final long asLong = (long) number;
            return asLong == (int) asLong ? JsonValue.of((int) asLong) : JsonValue.of(asLong);
        }
        return JsonValue.of(number);
    }

    /**
     * @return the JSON value of whichever typed column is set, or JSON {@code null} if none is.
     */
    static JsonValue fromColumns(@Nullable final Double number, @Nullable final String text,
            @Nullable final Boolean bool) {

        if (number != null) {
            return number(number);
        }
        if (text != null) {
            return JsonValue.of(text);
        }
        if (bool != null) {
            return JsonValue.of(bool);
        }
        return JsonValue.nullLiteral();
    }

    static String dataTypeOf(final JsonValue value) {
        if (value.isNumber()) {
            return "number";
        }
        if (value.isString()) {
            return "string";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        return "null";
    }
}
