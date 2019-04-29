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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Builder for creating instances of {@link ImmutableJsonArray}.
 */
@NotThreadSafe
final class ImmutableJsonArrayBuilder implements JsonArrayBuilder {

    private final List<JsonValue> values;

    private ImmutableJsonArrayBuilder() {
        values = new ArrayList<>();
    }

    /**
     * Creates a new {@code ImmutableJsonArrayBuilder} object.
     *
     * @return a new ImmutableJsonArrayBuilder object.
     */
    public static ImmutableJsonArrayBuilder newInstance() {
        return new ImmutableJsonArrayBuilder();
    }

    private static void checkValue(final Object value) {
        requireNonNull(value, "The value to be added must not be null!");
    }

    private static void checkFurtherValues(final Object furtherValues) {
        requireNonNull(furtherValues,
                "The further values to be added must not be null! If none are required just omit this argument.");
    }

    @Override
    public ImmutableJsonArrayBuilder add(final int value, final int... furtherValues) {
        checkFurtherValues(furtherValues);

        values.add(JsonFactory.newValue(value));
        addAll(Arrays.stream(furtherValues).mapToObj(JsonFactory::newValue));
        return this;
    }

    private void addAll(final Stream<JsonValue> jsonValueStream) {
        jsonValueStream.forEach(values::add);
    }

    @Override
    public ImmutableJsonArrayBuilder addIntegers(final Iterable<Integer> intValues) {
        return addAll(getStream(requireNonNull(intValues, "The int values to be added must not be null!"))
                .map(JsonFactory::newValue)
                .collect(Collectors.toList()));
    }

    @Override
    public JsonArrayBuilder set(final int index, final int value) {
        return set(index, JsonFactory.newValue(value));
    }

    @Override
    public ImmutableJsonArrayBuilder add(final long value, final long... furtherValues) {
        checkFurtherValues(furtherValues);

        values.add(JsonFactory.newValue(value));
        addAll(Arrays.stream(furtherValues).mapToObj(JsonFactory::newValue));
        return this;
    }

    @Override
    public ImmutableJsonArrayBuilder addLongs(final Iterable<Long> longValues) {
        return addAll(getStream(requireNonNull(longValues, "The long values to be added must not be null!"))
                .map(JsonFactory::newValue)
                .collect(Collectors.toList()));
    }

    @Override
    public JsonArrayBuilder set(final int index, final long value) {
        return set(index, JsonFactory.newValue(value));
    }

    @Override
    public ImmutableJsonArrayBuilder add(final double value, final double... furtherValues) {
        checkFurtherValues(furtherValues);

        values.add(JsonFactory.newValue(value));
        addAll(Arrays.stream(furtherValues).mapToObj(JsonFactory::newValue));
        return this;
    }

    @Override
    public ImmutableJsonArrayBuilder addDoubles(final Iterable<Double> doubleValues) {
        return addAll(getStream(requireNonNull(doubleValues, "The double values to be added must not be null!"))
                .map(JsonFactory::newValue)
                .collect(Collectors.toList()));
    }

    @Override
    public JsonArrayBuilder set(final int index, final double value) {
        return set(index, JsonFactory.newValue(value));
    }

    @Override
    public ImmutableJsonArrayBuilder add(final boolean value, final boolean... furtherValues) {
        checkFurtherValues(furtherValues);

        values.add(JsonFactory.newValue(value));
        for (final boolean furtherValue : furtherValues) {
            values.add(JsonFactory.newValue(furtherValue));
        }
        return this;
    }

    @Override
    public ImmutableJsonArrayBuilder addBooleans(final Iterable<Boolean> booleanValues) {
        return addAll(getStream(requireNonNull(booleanValues, "The boolean values to be added must not be null!"))
                .map(JsonFactory::newValue)
                .collect(Collectors.toList()));
    }

    @Override
    public JsonArrayBuilder set(final int index, final boolean value) {
        return set(index, JsonFactory.newValue(value));
    }

    @Override
    public ImmutableJsonArrayBuilder add(final String value, final String... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        final Collection<String> allStringValues = new ArrayList<>(1 + furtherValues.length);
        allStringValues.add(value);
        Collections.addAll(allStringValues, furtherValues);

        return addStrings(allStringValues);
    }

    @Override
    public ImmutableJsonArrayBuilder addStrings(final Iterable<String> stringValues) {
        return addAll(getStream(requireNonNull(stringValues, "The String values to be added must not be null!"))
                .map(JsonFactory::newValue)
                .collect(Collectors.toList()));
    }

    @Override
    public JsonArrayBuilder set(final int index, final String value) {
        requireNonNull(value, "The value to be set must not be null!");
        return set(index, JsonFactory.newValue(value));
    }

    private static <T> Stream<T> getStream(final Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    @Override
    public ImmutableJsonArrayBuilder add(final JsonValue value, final JsonValue... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        values.add(value);
        Collections.addAll(values, furtherValues);

        return this;
    }

    @Override
    public ImmutableJsonArrayBuilder addAll(final Iterable<? extends JsonValue> values) {
        values.forEach(this.values::add);

        return this;
    }

    @Override
    public JsonArrayBuilder set(final int index, final JsonValue value) {
        requireNonNull(value, "The value to be set must not be null!");
        values.set(index, value);
        return this;
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
    public JsonArrayBuilder remove(final int index) {
        values.remove(index);
        return this;
    }

    @Override
    public ImmutableJsonArrayBuilder remove(final JsonValue value) {
        requireNonNull(value, "The value to be removed must not be null!");

        values.remove(value);

        return this;
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return values.iterator();
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
    public Stream<JsonValue> stream() {
        return values.stream();
    }

    @Override
    public JsonArray build() {
        return ImmutableJsonArray.of(values);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonArrayBuilder that = (ImmutableJsonArrayBuilder) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "values=" + values +
                "]";
    }

}
