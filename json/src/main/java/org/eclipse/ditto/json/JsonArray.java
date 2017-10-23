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
package org.eclipse.ditto.json;

import java.util.Optional;

/**
 * Represents a JSON array. A JSON array is an ordered collection of JSON values. Duplicate values are permitted.
 * <p>
 * <em>Implementations of this interface are required to be immutable!</em>
 * </p>
 */
public interface JsonArray extends JsonValue, JsonValueContainer<JsonValue> {

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
