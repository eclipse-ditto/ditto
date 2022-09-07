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
package org.eclipse.ditto.json;

import java.io.IOException;

import javax.annotation.Nullable;

/**
 * Represents a JSON value. A JSON value can be
 * <ul>
 * <li>a literal like for example {@code true} or {@code null},</li>
 * <li>a number like for example {@code 42},</li>
 * <li>a string like for example {@code "foo"},</li>
 * <li>an array like for example {@code [1,2,3,23]} or</li>
 * <li>an object like for example
 * <pre>
 * {
 *   "foo": "bar",
 *   "bar": 43.3,
 *   "baz": ["a", "b", "c"]
 * }
 * </pre>
 * </li>
 * </ul>
 * For JSON objects and JSON arrays do exists dedicated interfaces which are {@link JsonObject} and {@link JsonArray}.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonValue {

    /**
     * Returns a JSON literal, which represents {@code null}.
     *
     * @return the literal.
     */
    static JsonValue nullLiteral() {
        return JsonFactory.nullLiteral();
    }

    /**
     * Returns a JSON literal that represents the given {@code boolean} value.
     *
     * @param value the value to get a JSON literal for.
     * @return a JSON literal that represents the given boolean value.
     */
    static JsonValue of(final boolean value) {
        return JsonFactory.newValue(value);
    }

    /**
     * Returns a JSON number that represents the given {@code int} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    static JsonNumber of(final int value) {
        return JsonFactory.newValue(value);
    }

    /**
     * Returns a JSON number that represents the given {@code long} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    static JsonNumber of(final long value) {
        return JsonFactory.newValue(value);
    }

    /**
     * Returns a JSON number that represents the given {@code double} value.
     *
     * @param value the value to get a JSON number for.
     * @return a JSON number that represents the given value.
     */
    static JsonNumber of(final double value) {
        return JsonFactory.newValue(value);
    }

    /**
     * Returns a JsonValue that represents the given Java string as JSON string.
     * For example the Java string {@code "foo"} would be {@code "\"foo\""} as JSON string.
     *
     * @param jsonString the string to get a JSON representation for.
     * @return a JSON value that represents the given string. If {@code jsonString} is {@code null}, a "null" object is
     * returned.
     * @throws IllegalArgumentException if {@code jsonString} is empty.
     * @see JsonFactory#nullLiteral()
     */
    static JsonValue of(@Nullable final String jsonString) {
        return JsonFactory.newValue(jsonString);
    }

    /**
     * Tries to guess the appropriate JsonValue for the given Java value.
     *
     * @param value the Java value to be converted to its JsonValue counterpart.
     * @param <T> the Java type to be converted.
     * @return the appropriate JsonValue.
     * @throws JsonParseException if {@code value} cannot be converted to a valid JSON value.
     */
    static <T> JsonValue of(@Nullable final T value) {
        return JsonFactory.getAppropriateValue(value);
    }

    /**
     * Indicates whether this value represents a boolean JSON literal.
     *
     * @return {@code true} this value represents a boolean JSON literal, {@code false} else.
     */
    boolean isBoolean();

    /**
     * Indicates whether this value represents a JSON number.
     *
     * @return {@code true} if this value represents a JSON number, {@code false} else.
     */
    boolean isNumber();

    /**
     * Indicates whether this value is an integer.
     *
     * @return {@code true} if an only if this value is an integer.
     */
    boolean isInt();

    /**
     * Indicates whether this value is of type long.
     *
     * @return {@code true} if an only if this value is of type long.
     */
    boolean isLong();

    /**
     * Indicates whether this value is of type double.
     *
     * @return {@code true} if an only if this value is of type double.
     */
    boolean isDouble();

    /**
     * Indicates whether this value represents a JSON string.
     *
     * @return {@code true} if this value represents a JSON string, {@code false} else.
     */
    boolean isString();

    /**
     * Indicates whether this value represents a JSON object.
     *
     * @return {@code true} if this value represents a JSON object, {@code false} else.
     */
    boolean isObject();

    /**
     * Indicates whether this value represents a JSON array.
     *
     * @return {@code true} if this value represents a JSON array, {@code false} else.
     */
    boolean isArray();

    /**
     * Indicates whether this value represents a {@code null} JSON literal.
     *
     * @return {@code true} this value represents a {@code null} JSON literal, {@code false} else.
     */
    boolean isNull();

    /**
     * Returns this JSON value as a {@code boolean} value, assuming that this value is either {@code true} or
     * {@code false}. If this is not the case, an exception is thrown.
     *
     * @return this value as Java {@code boolean}.
     * @throws UnsupportedOperationException if this value is neither {@code true} or {@code false}.
     * @see #isBoolean()
     */
    boolean asBoolean();

    /**
     * Returns this JSON value as an {@code int} value, assuming that this value represents a JSON number that can be
     * interpreted as Java {@code int}. If this is not the case, an exception is thrown.
     * <p>
     * To be interpreted as Java {@code int}, the JSON number must neither contain an exponent nor a fraction part.
     * Moreover, the number must be in the {@code Integer} range.
     * </p>
     *
     * @return this value as Java {@code int}.
     * @throws UnsupportedOperationException if this value is not a JSON number.
     * @throws NumberFormatException if this JSON number can not be interpreted as {@code int} value.
     * @see #isNumber()
     */
    int asInt();

    /**
     * Returns this JSON value as an {@code long} value, assuming that this value represents a JSON number that can be
     * interpreted as Java {@code long}. If this is not the case, an exception is thrown.
     * <p>
     * To be interpreted as Java {@code long}, the JSON number must neither contain an exponent nor a fraction part.
     * Moreover, the number must be in the {@code Long} range.
     * </p>
     *
     * @return this value as Java {@code long}.
     * @throws UnsupportedOperationException if this value is not a JSON number.
     * @throws NumberFormatException if this JSON number can not be interpreted as {@code long} value.
     * @see #isNumber()
     */
    long asLong();

    /**
     * Returns this JSON value as a {@code double} value, assuming that this value represents a JSON number. If this is
     * not the case, an exception is thrown.
     * <p>
     * If the JSON number is out of the {@code Double} range, {@link Double#POSITIVE_INFINITY} or
     * {@link Double#NEGATIVE_INFINITY} is returned.
     * </p>
     *
     * @return this value as Java {@code double}.
     * @throws UnsupportedOperationException if this value is not a JSON number
     */
    double asDouble();

    /**
     * Returns this JSON value as string, assuming that this value represents a JSON string. If this is not the case, an
     * exception is thrown.
     *
     * @return the (simple) Java string represented by this value i. e. the returned string is not quoted.
     * @throws UnsupportedOperationException if this value is not a JSON string.
     */
    String asString();

    /**
     * Returns this JSON value as {@link JsonObject}, assuming that this value represents a JSON object. If this is not
     * the case, an exception is thrown.
     *
     * @return a {@code JsonObject} for this value.
     * @throws UnsupportedOperationException if this value is not a JSON object.
     */
    JsonObject asObject();

    /**
     * Returns this JSON value as {@link JsonArray}, assuming that this value represents a JSON array. If this is not
     * the case, an exception is thrown.
     *
     * @return a {@code JsonArray} for this value.
     * @throws UnsupportedOperationException if this value is not a JSON array.
     */
    JsonArray asArray();

    /**
     * Returns the JSON string for this value in its minimal form, without any additional whitespace.
     *
     * @return a JSON string that represents this value. If this value is a JSON string, the returned string is
     * surrounded by quotes.
     */
    @Override
    String toString();

    /**
     * Return this JSON value in string representation, without quoting if this is a JSON string already.
     *
     * @return the string representation.
     */
    default String formatAsString() {
        return isString() ? asString() : toString();
    }

    /**
     * Writes this JsonValue into the provided serialization context.
     * This is intended to be used by serialization logic only.
     * Pass this object to the relevant methods in {@link JsonFactory} instead if you want its serialized
     * representation.
     *
     * @param serializationContext the context for serialization bundling configuration and state needed for
     * serialization.
     * @throws IOException in case writing the value to the backing OutputStream causes an IOException.
     * @since 1.1.0
     */
    void writeValue(SerializationContext serializationContext) throws IOException;


    /**
     * Returns an upper bound for the size (in chars), that the serialized version of this value might have.
     * The result of toString().length() on this object is guaranteed to be lower or equal.
     *
     * @return the upper bound as defined above.
     * @since 1.1.0
     */
    long getUpperBoundForStringSize();
}
