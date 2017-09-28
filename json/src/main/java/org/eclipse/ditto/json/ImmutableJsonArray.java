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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.Immutable;

/**
 * Represents a JSON array, i.e. an ordered collection of JSON values.
 * <p>
 * Each call to a method which would alter the state of this object returns a new JSON array with the altered state
 * instead while the old JSON array remains unchanged. Care has to be taken to assign the result of an altering method
 * like {@code add} to a variable to have a handle to the new resp. altered JSON array.
 * </p>
 */
@Immutable
final class ImmutableJsonArray extends AbstractImmutableJsonValue implements JsonArray {

    private final List<JsonValue> values;

    private ImmutableJsonArray(final List<JsonValue> theValues) {
        requireNonNull(theValues, "The JSON values must not be null!");

        values = Collections.unmodifiableList(new ArrayList<>(theValues));
    }

    /**
     * Returns a new empty JSON array.
     *
     * @return a new empty JSON array.
     */
    public static ImmutableJsonArray empty() {
        return new ImmutableJsonArray(Collections.emptyList());
    }

    /**
     * Returns a new JSON array which is based on the given JSON array of the Minimal Json project library.
     *
     * @param values the values to base the JSON array to be created on.
     * @return a new JSON array.
     * @throws NullPointerException if {@code minimalJsonArray} is {@code null}.
     */
    public static ImmutableJsonArray of(final List<JsonValue> values) {
        return new ImmutableJsonArray(values);
    }

    private static void checkValue(final Object value) {
        requireNonNull(value, "The value to add must not be null!");
    }

    private static void checkFurtherValues(final Object furtherValues) {
        requireNonNull(furtherValues,
                "The further values must not be null! If none are required just omit this argument.");
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public ImmutableJsonArray asArray() {
        return this;
    }

    @Override
    public ImmutableJsonArray add(final int value, final int... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonFactory::newValue)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final long value, final long... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonFactory::newValue)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final double value, final double... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonFactory::newValue)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final boolean value, final boolean... furtherValues) {
        checkFurtherValues(furtherValues);

        final List<JsonValue> valuesCopy = copyValues();
        valuesCopy.add(JsonFactory.newValue(value));
        for (final boolean furtherValue : furtherValues) {
            valuesCopy.add(JsonFactory.newValue(furtherValue));
        }

        return of(valuesCopy);
    }

    @Override
    public ImmutableJsonArray add(final String value, final String... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value),
                Arrays.stream(furtherValues)
                        .map(JsonFactory::newValue)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final JsonValue value, final JsonValue... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        final List<JsonValue> valuesCopy = copyValues();
        valuesCopy.add(value);
        Collections.addAll(valuesCopy, furtherValues);

        return of(valuesCopy);
    }

    @Override
    public Optional<JsonValue> get(final int index) {
        try {
            return Optional.of(values.get(index));
        } catch (final IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public int getSize() {
        return values.size();
    }

    @Override
    public boolean contains(final JsonValue value) {
        requireNonNull(value, "The value whose presence in this array is to be tested must not be null!");

        return values.contains(value);
    }

    @Override
    public int indexOf(final JsonValue value) {
        requireNonNull(value, "The value to search the index for must not be null!");

        return values.indexOf(value);
    }

    @Override
    public Stream<JsonValue> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public Iterator<JsonValue> iterator() {
        final List<JsonValue> valuesCopy = copyValues();
        return valuesCopy.iterator();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonArray that = (ImmutableJsonArray) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    protected String createStringRepresentation() {
        final com.eclipsesource.json.JsonArray minimalJsonArray = new com.eclipsesource.json.JsonArray();
        for (final JsonValue value : values) {
            minimalJsonArray.add(JsonFactory.convert(value));
        }
        return minimalJsonArray.toString();
    }

    private List<JsonValue> copyValues() {
        return new ArrayList<>(values);
    }

}
