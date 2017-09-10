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

import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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
     * Creates a new {@link ImmutableJsonArrayBuilder} object.
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
    public JsonArrayBuilder add(final int value, final int... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value), stream(furtherValues) //
                .mapToObj(JsonFactory::newValue) //
                .toArray(JsonValue[]::new));
    }

    @Override
    public JsonArrayBuilder add(final long value, final long... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value), stream(furtherValues) //
                .mapToObj(JsonFactory::newValue) //
                .toArray(JsonValue[]::new));
    }

    @Override
    public JsonArrayBuilder add(final double value, final double... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value), stream(furtherValues) //
                .mapToObj(JsonFactory::newValue) //
                .toArray(JsonValue[]::new));
    }

    @Override
    public JsonArrayBuilder add(final boolean value, final boolean... furtherValues) {
        checkFurtherValues(furtherValues);

        values.add(JsonFactory.newValue(value));
        for (final boolean furtherValue : furtherValues) {
            values.add(JsonFactory.newValue(furtherValue));
        }

        return this;
    }

    @Override
    public JsonArrayBuilder add(final String value, final String... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        return add(JsonFactory.newValue(value), stream(furtherValues) //
                .map(JsonFactory::newValue) //
                .toArray(JsonValue[]::new));
    }

    @Override
    public JsonArrayBuilder add(final JsonValue value, final JsonValue... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        values.add(value);
        Collections.addAll(values, furtherValues);

        return this;
    }

    @Override
    public JsonArrayBuilder addAll(final Iterable<? extends JsonValue> values) {
        values.forEach(this.values::add);

        return this;
    }

    @Override
    public JsonArrayBuilder remove(final JsonValue value) {
        requireNonNull(value, "The value to be removed must not be null!");

        values.remove(value);

        return this;
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return values.iterator();
    }

    @Override
    public JsonArray build() {
        return ImmutableJsonArray.of(values);
    }

}
