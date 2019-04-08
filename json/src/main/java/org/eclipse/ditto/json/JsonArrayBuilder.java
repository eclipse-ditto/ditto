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

import java.util.Optional;

/**
 * A mutable builder with a fluent API for a {@link JsonArray}. Implementations of this interface are normally not
 * thread safe and not reusable.
 */
public interface JsonArrayBuilder extends JsonValueContainer<JsonValue> {

    /**
     * Adds at least new int value to the JSON array to be built.
     *
     * @param value the int to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(int value, int... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param intValues the values to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code intValues} is {@code null}.
     */
    JsonArrayBuilder addIntegers(Iterable<Integer> intValues);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, int value);

    /**
     * Adds at least one long value to the JSON array to be built.
     *
     * @param value the long to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(long value, long... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param longValues the values to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code longValues} is {@code null}.
     */
    JsonArrayBuilder addLongs(Iterable<Long> longValues);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, long value);

    /**
     * Adds at least one double value to the JSON array to be built.
     *
     * @param value the double to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(double value, double... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param doubleValues the values to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code doubleValues} is {@code null}.
     */
    JsonArrayBuilder addDoubles(Iterable<Double> doubleValues);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, double value);

    /**
     * Adds at least one boolean value to the array to be built.
     *
     * @param value the boolean to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(boolean value, boolean... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param booleanValues the values to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code booleanValues} is {@code null}.
     */
    JsonArrayBuilder addBooleans(Iterable<Boolean> booleanValues);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, boolean value);

    /**
     * Adds at least one string value to the array to be built.
     *
     * @param value the string to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonArrayBuilder add(String value, String... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param stringValues the values to be added.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code stringValues} is {@code null}.
     */
    JsonArrayBuilder addStrings(Iterable<String> stringValues);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, String value);

    /**
     * Adds at least one {@link JsonValue} to the array to be built.
     *
     * @param value the JSON value to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonArrayBuilder add(JsonValue value, JsonValue... furtherValues);

    /**
     * Adds all given values to the array to be built.
     *
     * @param values the values to add to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code values} is {@code null}.
     */
    JsonArrayBuilder addAll(Iterable<? extends JsonValue> values);

    /**
     * Replaces the value at the specified position in the array to be built.
     *
     * @param index the position of the element to be set.
     * @param value the value to be placed at the specified position.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code value} is {@code null}.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder set(int index, JsonValue value);

    /**
     * Returns the JSON value at the specified position in the array to be built.
     *
     * @param index the index of the array value to be returned. If the index is out of bounds an empty Optional is
     * returned.
     * @return the JSON value at the specified position.
     */
    Optional<JsonValue> get(int index);

    /**
     * Removes the value at the specified position from the array to be built. This shifts any subsequent values to
     * the left (lowers their indices by one).
     *
     * @param index the index of the value to be removed.
     * @return this builder to allow method chaining.
     * @throws java.lang.IndexOutOfBoundsException if the index is out of range, i. e.
     * {@code index < 0 || index > getSize()}
     */
    JsonArrayBuilder remove(int index);

    /**
     * Removes the given value from the array to be built.
     *
     * @param value the value to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code value} is {@code null}.
     */
    JsonArrayBuilder remove(JsonValue value);

    /**
     * Creates a new {@link JsonArray} containing all values which were added beforehand.
     *
     * @return a new JSON array.
     */
    JsonArray build();

}
