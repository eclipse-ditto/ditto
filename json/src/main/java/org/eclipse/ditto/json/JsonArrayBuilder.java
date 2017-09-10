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

/**
 * A mutable builder for a {@link JsonArray}. Implementations of this interface are normally not thread safe and not
 * reusable.
 */
public interface JsonArrayBuilder extends Iterable<JsonValue> {

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
     * Adds at least one long value to the JSON array to be built.
     *
     * @param value the long to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(long value, long... furtherValues);

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
     * Adds at least one boolean value to the array to be built.
     *
     * @param value the boolean to add to the array.
     * @param furtherValues additional values to be added to the array.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code furtherValues} is {@code null}.
     */
    JsonArrayBuilder add(boolean value, boolean... furtherValues);

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
