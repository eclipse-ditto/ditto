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

import java.io.IOException;
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
import java.util.ServiceLoader;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
    private static final String ASSERTION_VALUES_OF_JSON_ARRAY = "The values of the JSON array must not be null!";

    private final SoftReferencedValueList valueList;

    ImmutableJsonArray(final SoftReferencedValueList theValueList) {
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
        return new ImmutableJsonArray(SoftReferencedValueList.of(values));
    }

    /**
     * Like {@link #of(List, String)}, but takes over the passed list instead of copying it - for parsers which build
     * the list exclusively for the new array. The caller must neither modify the list nor keep a reference to it.
     *
     * @param values the values of the JSON array, taken over.
     * @param stringRepresentation the already known string representation of the returned array or {@code null}.
     * @return the JSON array.
     */
    static ImmutableJsonArray ofTakingOwnership(final List<JsonValue> values,
            @Nullable final String stringRepresentation) {
        requireNonNull(values, ASSERTION_VALUES_OF_JSON_ARRAY);
        return new ImmutableJsonArray(SoftReferencedValueList.ofTakingOwnership(values, stringRepresentation));
    }

    /**
     * Creates a JSON array parsed from CBOR, taking over the passed values array and referencing its CBOR
     * representation as slice of {@code cborBytes}, which must never be modified.
     *
     * @param ownedValues the values, taken over; none of them is {@code null}.
     * @param cborBytes the bytes containing the CBOR representation.
     * @param cborOffset the offset of the CBOR representation in {@code cborBytes}.
     * @param cborLength the length of the CBOR representation.
     * @return the JSON array.
     */
    static ImmutableJsonArray ofCborSlice(final JsonValue[] ownedValues, final byte[] cborBytes,
            final int cborOffset, final int cborLength) {
        return new ImmutableJsonArray(
                SoftReferencedValueList.ofCborSlice(ownedValues, cborBytes, cborOffset, cborLength));
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
        requireNonNull(values, ASSERTION_VALUES_OF_JSON_ARRAY);
        return new ImmutableJsonArray(SoftReferencedValueList.of(values, stringRepresentation));
    }

    /**
     * Returns a new JSON array which is based on the given JSON array of the Minimal Json project library.
     *
     * @param values the values to base the JSON array to be created on.
     * @param cborRepresentation the already known CBOR representation of the returned array or {@code null}.
     * @return a new JSON array.
     * @throws NullPointerException if {@code values} is {@code null}.
     * @since 1.1.0
     */
    public static ImmutableJsonArray of(final List<JsonValue> values, @Nullable final byte[] cborRepresentation) {
        requireNonNull(values, ASSERTION_VALUES_OF_JSON_ARRAY);
        return new ImmutableJsonArray(SoftReferencedValueList.of(values, cborRepresentation));
    }

    /**
     * Returns a new JSON array which is based on the given JSON array of the Minimal Json project library.
     *
     * @param values the values to base the JSON array to be created on.
     * @param stringRepresentation the already known string representation of the returned array or {@code null}.
     * @param cborRepresentation the already known CBOR representation of the returned array or {@code null}.
     * @return a new JSON array.
     * @throws NullPointerException if {@code values} is {@code null}.
     * @since 1.1.0
     */
    public static ImmutableJsonArray of(final List<JsonValue> values, @Nullable final String stringRepresentation,
            @Nullable final byte[] cborRepresentation) {
        requireNonNull(values, ASSERTION_VALUES_OF_JSON_ARRAY);
        return new ImmutableJsonArray(SoftReferencedValueList.of(values, stringRepresentation, cborRepresentation));
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

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        valueList.writeValue(serializationContext);
    }

    @Override
    public long getUpperBoundForStringSize() {
        return valueList.upperBoundForStringSize();
    }

    @Immutable
    static final class SoftReferencedValueList {

        private static final long CBOR_MAX_COMPRESSION_RATIO = 5; // "false" compressed to one byte
        private static final CborFactory CBOR_FACTORY;

        static {
            final ServiceLoader<CborFactory> sl = ServiceLoader.load(CborFactory.class);
            CBOR_FACTORY = StreamSupport.stream(sl.spliterator(), false)
                    .findFirst()
                    .orElseGet(NoopCborFactory::new); // when no Service could be found -> CBOR not available
        }

        // Same lazy-encoding invariant as ImmutableJsonObject.SoftReferencedFieldMap: whenever valuesRef holds a
        // SoftReference, at least one representation must be non-null so recoverValues() can rebuild the list after
        // a soft-clear. The CBOR representation is final, the lazily created string representation volatile.
        private volatile String jsonArrayStringRepresentation;
        // slice of a byte array possibly shared with the enclosing and nested values parsed from the same message
        @Nullable private final byte[] cborBytes;
        private final int cborOffset;
        private final int cborLength;
        private int hashCode;
        // Either the List<JsonValue> held strongly (while no serialised representation exists to recover it from)
        // or a SoftReference<List<JsonValue>> once a representation is available. values() unwraps both cases.
        private volatile Object valuesRef;

        private SoftReferencedValueList(final List<JsonValue> jsonValueList,
                @Nullable final String stringRepresentation,
                @Nullable final byte[] cborArrayRepresentation) {
            this(Collections.unmodifiableList(new ArrayList<>(jsonValueList)), stringRepresentation,
                    cborArrayRepresentation, 0, null != cborArrayRepresentation ? cborArrayRepresentation.length : 0);
        }

        private SoftReferencedValueList(final List<JsonValue> immutable,
                @Nullable final String stringRepresentation,
                @Nullable final byte[] cborBytes, final int cborOffset, final int cborLength) {
            jsonArrayStringRepresentation = stringRepresentation;
            this.cborBytes = cborBytes;
            this.cborOffset = cborOffset;
            this.cborLength = cborLength;
            if (stringRepresentation != null || cborBytes != null) {
                valuesRef = new SoftReference<>(immutable);
            } else {
                valuesRef = immutable;
            }
            hashCode = 0;
        }

        static SoftReferencedValueList empty() {
            return of(Collections.emptyList(), "[]", new byte[]{(byte) 0x80});
        }

        static SoftReferencedValueList of(final List<JsonValue> values) {
            return new SoftReferencedValueList(values, null, null);
        }

        static SoftReferencedValueList ofTakingOwnership(final List<JsonValue> values,
                @Nullable final String stringRepresentation) {
            // parsed values are never null: an exactly sized immutable list, without wrapper and spare capacity
            return new SoftReferencedValueList(new CompactValueList(values.toArray(new JsonValue[0])),
                    stringRepresentation, null, 0, 0);
        }

        static SoftReferencedValueList ofCborSlice(final JsonValue[] ownedValues, final byte[] cborBytes,
                final int cborOffset, final int cborLength) {
            return new SoftReferencedValueList(new CompactValueList(ownedValues), null, cborBytes, cborOffset,
                    cborLength);
        }

        static SoftReferencedValueList of(final List<JsonValue> jsonValueList,
                @Nullable final String stringRepresentation) {
            return new SoftReferencedValueList(jsonValueList, stringRepresentation, null);
        }

        static SoftReferencedValueList of(final List<JsonValue> jsonValueList,
                @Nullable final byte[] cborRepresentation) {
            return new SoftReferencedValueList(jsonValueList, null, cborRepresentation);
        }

        static SoftReferencedValueList of(final List<JsonValue> jsonValueList,
                @Nullable final String stringRepresentation,
                @Nullable final byte[] cborRepresentation) {
            return new SoftReferencedValueList(jsonValueList, stringRepresentation, cborRepresentation);
        }

        private String createStringRepresentation(final Iterable<JsonValue> jsonValues) {
            final StringBuilder stringBuilder = new StringBuilder(guessSerializedSize());
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
            final Object ref = valuesRef;
            if (ref instanceof SoftReference) {
                @SuppressWarnings("unchecked")
                final SoftReference<List<JsonValue>> softRef = (SoftReference<List<JsonValue>>) ref;
                List<JsonValue> result = softRef.get();
                if (null == result) {
                    result = recoverValues();
                    valuesRef = new SoftReference<>(result);
                }
                return result;
            }
            @SuppressWarnings("unchecked")
            final List<JsonValue> strong = (List<JsonValue>) ref;
            return strong;
        }

        private void softenValuesRef(final List<JsonValue> values) {
            if (!(valuesRef instanceof SoftReference)) {
                valuesRef = new SoftReference<>(values);
            }
        }

        private List<JsonValue> recoverValues() {
            final List<JsonValue> recovered;
            final byte[] bytes = cborBytes;
            if (CBOR_FACTORY.isCborAvailable() && bytes != null) {
                // the slice is immutable and owned by this array: parse it in place, the nested values slice it
                return ((ImmutableJsonArray) CBOR_FACTORY.readFromOwnedBytes(bytes, cborOffset, cborLength))
                        .valueList.values();
            } else if (jsonArrayStringRepresentation != null) {
                recovered = parseToList(jsonArrayStringRepresentation);
            } else {
                throw new IllegalStateException("Fatal cache miss on JsonArray");
            }
            // keep the recovered list immutable, like the one passed to the constructor
            return Collections.unmodifiableList(recovered);
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
            if (jsonArrayStringRepresentation != null && that.jsonArrayStringRepresentation != null) {
                if (jsonArrayStringRepresentation.equals(that.jsonArrayStringRepresentation)) {
                    return true;
                } else if (jsonArrayStringRepresentation.length() == that.jsonArrayStringRepresentation.length()) {
                    return Objects.equals(values(), that.values());
                }
                return false;
            }
            if (cborBytes != null && that.cborBytes != null &&
                    CompactFieldMap.rangeEquals(cborBytes, cborOffset, cborLength,
                            that.cborBytes, that.cborOffset, that.cborLength)) {
                return true;
            }
            return Objects.equals(values(), that.values());
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
            if (jsonArrayStringRepresentation == null) {
                final List<JsonValue> currentValues = values();
                jsonArrayStringRepresentation = createStringRepresentation(currentValues);
                softenValuesRef(currentValues);
            }
            return jsonArrayStringRepresentation;
        }

        void writeValue(final SerializationContext serializationContext) throws IOException {
            final byte[] bytes = cborBytes;
            if (null != bytes) {
                serializationContext.writeCachedElement(bytes, cborOffset, cborLength);
            } else {
                // write the values as part of the context's output instead of creating a CBOR representation first
                CBOR_FACTORY.writeArray(values(), serializationContext);
            }
        }

        private int guessSerializedSize() {
            // This function currently overestimates for CBOR and underestimates for JSON, but it should be better
            //  than a static guess
            if (jsonArrayStringRepresentation != null) {
                return jsonArrayStringRepresentation.length();
            }
            if (cborBytes != null) {
                return cborLength;
            }
            return 512;
        }

        public long upperBoundForStringSize() {
            // callers need a real bound: materialise the string representation lazily when neither form exists
            if (jsonArrayStringRepresentation == null && cborBytes == null) {
                asJsonArrayString();
            }
            long max = 0L;
            if (jsonArrayStringRepresentation != null) {
                max = jsonArrayStringRepresentation.length();
            }
            if (cborBytes != null) {
                final long cborSize = cborLength * CBOR_MAX_COMPRESSION_RATIO;
                if (cborSize > max) {
                    max = cborSize;
                }
            }
            return max;
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
