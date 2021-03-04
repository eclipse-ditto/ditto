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

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

/**
 * Represents a JSON array. A JSON array is an ordered collection of JSON values. Duplicate values are permitted.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonArray extends JsonValue, JsonValueContainer<JsonValue> {

    /**
     * Creates a new {@code JsonArray} from the given string.
     *
     * @param jsonArrayString the string that represents the JSON array.
     * @return the JSON array that has been created from the string.
     * @throws NullPointerException if {@code jsonArrayString} is {@code null}.
     * @throws IllegalArgumentException if {@code jsonArrayString} is empty.
     * @throws JsonParseException if {@code jsonArrayString} does not represent a valid JSON array.
     */
    static JsonArray of(final String jsonArrayString) {
        return JsonFactory.newArray(jsonArrayString);
    }

    /**
     * Returns an empty {@code JsonArray} object.
     *
     * @return the empty array.
     */
    static JsonArray empty() {
        return JsonFactory.newArray();
    }

    /**
     * Returns an instance of {@code JsonArray} which contains the given values.
     * This method tries to determine the appropriate {@link JsonValue}-counterpart for each
     * given value.
     *
     * @param <T> the type of values for JsonArray
     * @param value the mandatory value of the returned JsonArray.
     * @param furtherValues further optional values of the returned JsonArray.
     * @return the JsonArray.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     * @throws JsonParseException if either {@code value} or any item of {@code furtherValues}
     * cannot be converted to {@code JsonValue}.
     */
    @SuppressWarnings("unchecked")
    static <T> JsonArray of(@Nullable final T value, final T... furtherValues) {
        final JsonArrayBuilder arrayBuilder = newBuilder();
        arrayBuilder.add(JsonFactory.getAppropriateValue(value));
        for (final T furtherValue : furtherValues) {
            arrayBuilder.add(JsonFactory.getAppropriateValue(furtherValue));
        }
        return arrayBuilder.build();
    }

    /**
     * Returns an instance of {@code JsonArray} which contains the given values.
     * This method tries to determine the appropriate {@link JsonValue}-counterpart for each
     * item of the specified Iterable.
     *
     * @param <T> the type of values for JsonArray
     * @param values the values of the returned JsonArray. {@code null}  items are
     * @return the JsonArray.
     * @throws NullPointerException if {@code values} is {@code null}.
     * @throws JsonParseException if any item of {@code values} cannot be converted to
     * {@code JsonValue}.
     */
    static <T> JsonArray of(final Iterable<T> values) {
        requireNonNull(values, "The values of the array must not be null!");
        if (values instanceof JsonValue) {
            return JsonFactory.newArrayBuilder().add((JsonValue) values).build();
        }
        return StreamSupport.stream(values.spliterator(), false)
                .map(JsonFactory::getAppropriateValue)
                .collect(JsonCollectors.valuesToArray());
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code JsonArray}.
     *
     * @return a new JSON array builder.
     */
    static JsonArrayBuilder newBuilder() {
        return JsonFactory.newArrayBuilder();
    }

    /**
     * Returns a new mutable builder with a fluent API for a {@code JsonArray}. The returned builder is already
     * initialised with the data of the this JSON array. This method is useful if an existing JSON array should be
     * strongly modified but the amount of creating objects should be kept low at the same time.
     *
     * @return a new JSON array builder with pre-filled data of this JSON array.
     */
    default JsonArrayBuilder toBuilder() {
        return JsonFactory.newArrayBuilder(this);
    }

    /**
     * Creates a new JSON array by appending the JSON representation of the specified int value to the end of this
     * array.
     *
     * @param value the int value to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArray add(int value, int... furtherValues);

    /**
     * Creates a new JSON array by appending the JSON representation of the specified long value to the end of this
     * array.
     *
     * @param value the long value to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArray add(long value, long... furtherValues);

    /**
     * Creates a new JSON array by appending the JSON representation of the specified double value to the end of this
     * array.
     *
     * @param value the double value to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArray add(double value, double... furtherValues);

    /**
     * Creates a new JSON array by appending the JSON representation of the specified boolean value to the end of this
     * array.
     *
     * @param value the boolean value to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArray add(boolean value, boolean... furtherValues);

    /**
     * Creates a new JSON array by appending the JSON representation of the specified string value to the end of this
     * array.
     *
     * @param value the string to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonArray add(String value, String... furtherValues);

    /**
     * Creates a new JSON array by appending the specified value to the end of this array.
     *
     * @param value the value to be added to the array.
     * @param furtherValues additional values to be added to the array.
     * @return the a new JSON array which differs from this array by containing the specified value at its end.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonArray add(JsonValue value, JsonValue... furtherValues);

    /**
     * Returns the JSON value at the specified position in this array.
     *
     * @param index the index of the array value to be returned. If the index is out of bounds an empty Optional is
     * returned.
     * @return the JSON value at the specified position.
     */
    Optional<JsonValue> get(int index);

    /**
     * Indicates whether this JSON array contains the specified value.
     *
     * @param value the value whose presence in this array is to be tested.
     * @return {@code true} if this array contains the specified value, {@code false} else.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    boolean contains(JsonValue value);

    /**
     * Returns the index of the first occurrence of the specified value in this array, or {@code -1} if this array does
     * not contain the element.
     *
     * @param value the value to be searched for.
     * @return the index of the first occurrence of the specified value in this array, or {@code -1} if this array does
     * not contain the value at all.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    int indexOf(JsonValue value);

}
