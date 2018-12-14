/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.json;

import static java.util.Objects.requireNonNull;

import java.lang.ref.SoftReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Represents a JSON array, i.e. an ordered collection of JSON values.
 * <p>
 * Each call to a method which would alter the state of this object returns a new JSON array with the altered state
 * instead while the old JSON array remains unchanged. Care has to be taken to assign the result of an altering method
 * like {@code add} to a variable to have a handle to the new resp. altered JSON array.
 * </p>
 */
@Immutable
final class ImmutableJsonArray extends AbstractJsonValue implements JsonArray {

    @Nullable private static ImmutableJsonArray emptyInstance = null;

    private final SoftReferencedValueList valueList;

    private ImmutableJsonArray(final SoftReferencedValueList theValueList) {
        valueList = theValueList;
    }

    /**
     * Returns a new empty JSON array.
     *
     * @return a new empty JSON array.
     */
    public static ImmutableJsonArray empty() {
        ImmutableJsonArray result = emptyInstance;
        if (null == result) {
            result = new ImmutableJsonArray(SoftReferencedValueList.empty());
            emptyInstance = result;
        }
        return result;
    }

    /**
     * Returns a new JSON array which is based on the given JSON array of the Minimal Json project library.
     *
     * @param values the values to base the JSON array to be created on.
     * @return a new JSON array.
     * @throws NullPointerException if {@code values} is {@code null}.
     */
    public static ImmutableJsonArray of(final List<JsonValue> values) {
        return of(values, null);
    }

    /**
     * Returns a new JSON array which is based on the given JSON array of the Minimal Json project library.
     *
     * @param values the values to base the JSON array to be created on.
     * @param stringRepresentation the already known string representation of the returned array or {@code null}.
     * @return a new JSON array.
     * @throws NullPointerException if {@code values} is {@code null}.
     */
    public static ImmutableJsonArray of(final List<JsonValue> values, @Nullable final String stringRepresentation) {
        requireNonNull(values, "The values of the JSON array must not be null!");
        return new ImmutableJsonArray(SoftReferencedValueList.of(values, stringRepresentation));
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

        return add(JsonValue.of(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonValue::of)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final long value, final long... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonValue.of(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonValue::of)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final double value, final double... furtherValues) {
        checkFurtherValues(furtherValues);

        return add(JsonValue.of(value),
                Arrays.stream(furtherValues)
                        .mapToObj(JsonValue::of)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final boolean value, final boolean... furtherValues) {
        checkFurtherValues(furtherValues);

        SoftReferencedValueList extendedValueList = valueList.add(JsonValue.of(value));
        for (final boolean furtherValue : furtherValues) {
            extendedValueList = extendedValueList.add(JsonValue.of(furtherValue));
        }

        return new ImmutableJsonArray(extendedValueList);
    }

    @Override
    public ImmutableJsonArray add(final String value, final String... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        return add(JsonValue.of(value),
                Arrays.stream(furtherValues)
                        .map(JsonValue::of)
                        .toArray(JsonValue[]::new));
    }

    @Override
    public ImmutableJsonArray add(final JsonValue value, final JsonValue... furtherValues) {
        checkValue(value);
        checkFurtherValues(furtherValues);

        SoftReferencedValueList extendedValueList = valueList.add(value);
        for (final JsonValue furtherValue : furtherValues) {
            extendedValueList = extendedValueList.add(furtherValue);
        }

        return new ImmutableJsonArray(extendedValueList);
    }

    @Override
    public Optional<JsonValue> get(final int index) {
        try {
            return Optional.of(valueList.get(index));
        } catch (final IndexOutOfBoundsException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isEmpty() {
        return valueList.isEmpty();
    }

    @Override
    public int getSize() {
        return valueList.getSize();
    }

    @Override
    public boolean contains(final JsonValue value) {
        requireNonNull(value, "The value whose presence in this array is to be tested must not be null!");

        return valueList.contains(value);
    }

    @Override
    public int indexOf(final JsonValue value) {
        requireNonNull(value, "The value to search the index for must not be null!");

        return valueList.indexOf(value);
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return valueList.getIterator();
    }

    @Override
    public Stream<JsonValue> stream() {
        return valueList.getStream();
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
        return Objects.equals(valueList, that.valueList);
    }

    @Override
    public int hashCode() {
        return valueList.hashCode();
    }

    @Override
    public String toString() {
        return valueList.asJsonArrayString();
    }

    @Immutable
    static final class SoftReferencedValueList {

        private final String jsonArrayStringRepresentation;
        private int hashCode;
        private SoftReference<List<JsonValue>> valuesReference;

        private SoftReferencedValueList(final List<JsonValue> jsonValueList, final String stringRepresentation) {
            jsonArrayStringRepresentation = stringRepresentation;
            valuesReference = new SoftReference<>(Collections.unmodifiableList(new ArrayList<>(jsonValueList)));
            hashCode = 0;
        }

        static SoftReferencedValueList empty() {
            return of(Collections.emptyList(), "[]");
        }

        static SoftReferencedValueList of(final List<JsonValue> values) {
            return of(values, null);
        }

        static SoftReferencedValueList of(final List<JsonValue> jsonValueList,
                @Nullable final String stringRepresentation) {

            if (null != stringRepresentation) {
                return new SoftReferencedValueList(jsonValueList, stringRepresentation);
            }
            return new SoftReferencedValueList(jsonValueList, createStringRepresentation(jsonValueList));
        }

        private static String createStringRepresentation(final Iterable<JsonValue> jsonValues) {
            final StringBuilder stringBuilder = new StringBuilder(512);
            stringBuilder.append('[');
            String delimiter = "";
            for (final JsonValue jsonValue : jsonValues) {
                stringBuilder.append(delimiter);
                stringBuilder.append(jsonValue);
                delimiter = ",";
            }
            stringBuilder.append(']');

            return stringBuilder.toString();
        }

        JsonValue get(final int index) {
            return values().get(index);
        }

        boolean isEmpty() {
            return values().isEmpty();
        }

        int getSize() {
            return values().size();
        }

        boolean contains(final JsonValue value) {
            return values().contains(value);
        }

        int indexOf(final JsonValue value) {
            return values().indexOf(value);
        }

        SoftReferencedValueList add(final JsonValue jsonValue) {
            final List<JsonValue> valuesCopy = copyValues();
            valuesCopy.add(jsonValue);
            return of(valuesCopy);
        }

        private List<JsonValue> copyValues() {
            return new ArrayList<>(values());
        }

        private List<JsonValue> values() {
            List<JsonValue> result = valuesReference.get();
            if (null == result) {
                result = parseToList(jsonArrayStringRepresentation);
                valuesReference = new SoftReference<>(result);
            }
            return result;
        }

        private static List<JsonValue> parseToList(final String jsonArrayString) {
            final ValueListJsonHandler jsonHandler = new ValueListJsonHandler();
            JsonValueParser.fromString(jsonHandler).accept(jsonArrayString);
            return jsonHandler.getValue();
        }

        Iterator<JsonValue> getIterator() {
            return values().iterator();
        }

        Stream<JsonValue> getStream() {
            return values().stream();
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final SoftReferencedValueList that = (SoftReferencedValueList) o;
            if (jsonArrayStringRepresentation.equals(that.jsonArrayStringRepresentation)) {
                return true;
            } else if (jsonArrayStringRepresentation.length() == that.jsonArrayStringRepresentation.length()) {
                return Objects.equals(values(), that.values());
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = hashCode;
            if (0 == result) {
                result = values().hashCode();
                hashCode = result;
            }
            return result;
        }

        String asJsonArrayString() {
            return jsonArrayStringRepresentation;
        }

    }

    /**
     * This JsonHandler creates a List instead of a JsonArray as List is the internal structure of ImmutableJsonArray.
     * All method calls which do not affect JSON array creation are delegated to {@link DefaultDittoJsonHandler}.
     * JSON array creation has to be split because only the base level should be represented as a List, all nested
     * JSON arrays should be of type {@link JsonArray}.
     * <p>
     * <em>This handler is only usable for parsing JSON array strings.</em>
     * </p>
     */
    @NotThreadSafe
    static final class ValueListJsonHandler
            extends DittoJsonHandler<List<JsonValue>, List<JsonField>, List<JsonValue>> {

        private final DefaultDittoJsonHandler defaultHandler;
        private List<JsonValue> value;
        private final Deque<List<JsonValue>> jsonArrayBuilders; // for nested JsonArrays
        private int level;

        /**
         * Constructs a new {@code ValueListJsonHandler} object.
         */
        ValueListJsonHandler() {
            defaultHandler = DefaultDittoJsonHandler.newInstance();
            value = null;
            jsonArrayBuilders = new ArrayDeque<>();
            level = 0;
        }

        @Override
        public List<JsonValue> startArray() {
            if (0 < level) {
                jsonArrayBuilders.push(defaultHandler.startArray());
            }
            final List<JsonValue> result = new ArrayList<>();
            level++;
            return result;
        }

        @Override
        public List<JsonField> startObject() {
            return defaultHandler.startObject();
        }

        @Override
        public void endNull() {
            defaultHandler.endNull();
        }

        @Override
        public void endBoolean(final boolean value) {
            defaultHandler.endBoolean(value);
        }

        @Override
        public void endString(final String string) {
            defaultHandler.endString(string);
        }

        @Override
        public void endNumber(final String string) {
            defaultHandler.endNumber(string);
        }

        @Override
        public void endArrayValue(final List<JsonValue> jsonValues) {
            final List<JsonValue> jsonArrayBuilder = jsonArrayBuilders.peek();
            if (null != jsonArrayBuilder) {
                defaultHandler.endArrayValue(jsonArrayBuilder);
            } else {
                jsonValues.add(defaultHandler.getValue());
            }
        }

        @Override
        public void endArray(final List<JsonValue> jsonValues) {
            final List<JsonValue> jsonArrayBuilder = jsonArrayBuilders.poll();
            if (null != jsonArrayBuilder) {
                defaultHandler.endArray(jsonArrayBuilder);
            } else {
                value = new ArrayList<>(jsonValues);
            }
            level--;
        }

        @Override
        public void endObject(final List<JsonField> jsonFields) {
            defaultHandler.endObject(jsonFields);
        }

        @Override
        public void endObjectValue(final List<JsonField> jsonFields, final String name) {
            defaultHandler.endObjectValue(jsonFields, name);
        }

        @Override
        protected List<JsonValue> getValue() {
            return value;
        }

    }

}
