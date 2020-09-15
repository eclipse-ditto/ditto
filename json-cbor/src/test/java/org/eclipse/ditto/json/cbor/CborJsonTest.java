/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.json.cbor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.json.JsonFactory.newObject;
import static org.eclipse.ditto.json.JsonFactory.newValue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

public final class CborJsonTest {

    private static final String KNOWN_STRING_VALUE = "foo";
    private static final int KNOWN_INT_42 = 42;
    private static final JsonKey KNOWN_KEY_FOO = JsonKey.of("foo");
    private static final JsonKey KNOWN_KEY_BAR = JsonKey.of("bar");
    private static final JsonKey KNOWN_KEY_BAZ = JsonKey.of("baz");
    private static final JsonValue KNOWN_VALUE_FOO = JsonValue.of("bar");
    private static final JsonValue KNOWN_VALUE_BAR = JsonValue.of("baz");
    private static final JsonValue KNOWN_VALUE_BAZ = JsonValue.of(KNOWN_INT_42);
    private static final Map<String, JsonField> KNOWN_FIELDS = new LinkedHashMap<>();

    private static final int KNOWN_INT_0 = 23;
    private static final int KNOWN_INT_1 = 42;
    private static final int KNOWN_INT_2 = 1337;
    private static final List<JsonValue> KNOWN_INT_VALUE_LIST = new ArrayList<>();

    static {
        KNOWN_FIELDS.put(KNOWN_KEY_FOO.toString(), toField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        KNOWN_FIELDS.put(KNOWN_KEY_BAR.toString(), toField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        KNOWN_FIELDS.put(KNOWN_KEY_BAZ.toString(), toField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        KNOWN_INT_VALUE_LIST.add(JsonValue.of(KNOWN_INT_0));
        KNOWN_INT_VALUE_LIST.add(JsonValue.of(KNOWN_INT_1));
        KNOWN_INT_VALUE_LIST.add(JsonValue.of(KNOWN_INT_2));
    }

    private static JsonField toField(final CharSequence key, final JsonValue value) {
        return JsonField.newInstance(JsonKey.of(key), value);
    }

    private CborFactory cborFactory;

    @Before
    public void setup() {
        cborFactory = new JacksonCborFactory();
    }

    @Test
    public void writeImmutableJsonObjectNullWritesExpected() throws IOException {
        assertThat(CborTestUtils.serializeToHexString(JsonFactory.nullObject()))
                .isEqualToIgnoringCase(CborTestUtils.serializeToHexString(JsonFactory.nullLiteral()));
    }

    @Test
    public void writeImmutableJsonArrayNullWritesExpected() throws IOException {
        assertThat(CborTestUtils.serializeToHexString(JsonFactory.nullLiteral()))
                .isEqualToIgnoringCase(CborTestUtils.serializeToHexString(JsonFactory.nullArray()));
    }

    @Test
    public void writeImmutableJsonNullWritesExpected() throws IOException {
        final String expectedStringForNull = "F6";
        assertThat(CborTestUtils.serializeToHexString(JsonFactory.nullLiteral()))
                .isEqualToIgnoringCase(expectedStringForNull);
    }

    @Test
    public void writeImmutableJsonBooleanWritesExpected() throws IOException {
        final JsonValue thetruth = JsonValue.of(true);
        final JsonValue nottrue = JsonValue.of(false);

        final String thetruthExpectedString = "F5";
        final String nottrueExpectedString = "F4";

        assertThat(CborTestUtils.serializeToHexString(thetruth)).isEqualToIgnoringCase(thetruthExpectedString);
        assertThat(CborTestUtils.serializeToHexString(nottrue)).isEqualToIgnoringCase(nottrueExpectedString);
    }

    @Test
    public void writeImmutableJsonStringWritesExpectedSimple() throws IOException {
        final String expectedString = "63666F6F"; // "foo"
        final JsonValue underTest = JsonValue.of(KNOWN_STRING_VALUE);
        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(expectedString);
    }

    @Test
    public void writeImmutableJsonStringWritesExpectedComplex() throws IOException {
        final String expectedString = "75666F6F212F373840E282AC225CC384C2A77B7D5B5D"; // "foo!/78@€\"\\Ä§{}[]"
        final JsonValue underTest = JsonValue.of("foo!/78@€\"\\Ä§{}[]");
        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(expectedString);
    }

    @Test
    public void writeImmutableJsonIntWritesExpected() throws IOException {
        Map<String, Integer> testVectors = new HashMap<String, Integer>(){{
            put("1A7FFFFFFF", Integer.MAX_VALUE);
            put("3A7FFFFFFF", Integer.MIN_VALUE);
            put("00", 0);
            put("190539", 1337);
        }};

        for (Map.Entry<String, Integer> entry : testVectors.entrySet()) {
            String actual = CborTestUtils.serializeToHexString(JsonValue.of(entry.getValue()));
            String expected = entry.getKey();
            assertThat(actual).isEqualToIgnoringCase(expected);
        }
    }

    @Test
    public void writeImmutableJsonLongWritesExpected() throws IOException {
        Map<String, Long> testVectors = new HashMap<String, Long>(){{
            put("1B7FFFFFFFFFFFFFFF", Long.MAX_VALUE);
            put("3B7FFFFFFFFFFFFFFF", Long.MIN_VALUE);
            put("00", 0L);
            put("1A01655F8F", 23420815L);
        }};

        for (Map.Entry<String, Long> entry : testVectors.entrySet()) {
            String actual = CborTestUtils.serializeToHexString(JsonValue.of(entry.getValue()));
            String expected = entry.getKey();
            assertThat(actual).isEqualToIgnoringCase(expected);
        }
    }

    @Test
    public void writeImmutableJsonDoubleWritesExpectedTestVectors() throws IOException {
        /*
         * Since both Java and the CBOR library have no native support for 16bit Half-Precision Floating Points,
         * this implementation encodes them as 32bit floats instead, which increases serialized size but is understood
         * by other implementations.
         * The CBOR specification does not explicitly require implementations to choose the smallest representation possible.
         */
        Map<String, Double> testVectors = new HashMap<String, Double>(){{
            // representable as 16bit but represented as 32bit instead
            put("fa7f800000", Double.POSITIVE_INFINITY);
            put("faff800000", Double.NEGATIVE_INFINITY);
            put("fa33800000", 5.960464477539063e-8);

            // representable as 16bit but represented as 64bit instead
            put("fb7ff8000000000000", Double.NaN);

            // correctly represented as 32bit
            put("fa7f7fffff", 3.4028234663852886e+38);

            // correctly represented as 64bit
            put("FB0000000000000001", Double.MIN_VALUE);
            put("FB7FEFFFFFFFFFFFFF", Double.MAX_VALUE);
            put("FB402ABF972474538F", 13.3742D);
            put("FB59B05C5EED0FB82F", 1.081542E124D);
        }};

        for (Map.Entry<String, Double> entry : testVectors.entrySet()) {
            String actual = CborTestUtils.serializeToHexString(JsonValue.of(entry.getValue()));
            String expected = entry.getKey();
            assertThat(actual).isEqualToIgnoringCase(expected);
        }
    }

    @Test
    public void writeImmutableJsonObjectWritesExpectedEmpty() throws IOException {
        assertThat(CborTestUtils.serializeToHexString(JsonObject.empty())).isEqualToIgnoringCase("A0");
    }

    @Test
    public void writeImmutableJsonObjectWritesExpectedSimple() throws IOException {
        String expectedString = "A3" // map of length 3
                + "63666F6F" + "63626172" // "foo": "bar"
                + "63626172" + "6362617A" // "bar": "baz"
                + "6362617A" + "182A"; // "baz": 42

        assertThat(CborTestUtils.serializeToHexString(JsonFactory.newObjectBuilder(KNOWN_FIELDS.values()).build())) // {"foo":"bar","bar":"baz","baz":42}
                .isEqualToIgnoringCase(expectedString);
    }

    @Test
    public void validateImmutableJsonObjectInternalCachingBehaviour() throws IOException {
        final JsonObject objectWithSelfGeneratedCache = JsonFactory.newObjectBuilder(KNOWN_FIELDS.values()).build();
        assertInternalCachesAreAsExpected(objectWithSelfGeneratedCache, true, false);

        final ByteBuffer byteBuffer = cborFactory.toByteBuffer(objectWithSelfGeneratedCache);
        final JsonObject objectWithCborCache = cborFactory.readFrom(byteBuffer).asObject();
        assertInternalCachesAreAsExpected(objectWithSelfGeneratedCache, true, false);
        final JsonObject objectWithJsonCache = JsonFactory.newObject(objectWithSelfGeneratedCache.toString());
        assertInternalCachesAreAsExpected(objectWithSelfGeneratedCache, true, true);

        assertInternalCachesAreAsExpected(objectWithCborCache, true, false);
        assertInternalCachesAreAsExpected(objectWithJsonCache, false, true);
    }

    @Test
    public void writeImmutableJsonArrayWritesExpectedForSimpleArray() throws IOException {
        final String expectedString
                = "83" // Array of size 3
                + "17" // unsigned 23
                + "182A" // unsigned 42
                + "190539"; // unsigned 1337
        final JsonArray underTest = JsonFactory.newArrayBuilder(KNOWN_INT_VALUE_LIST).build();

        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(expectedString);
    }

    @Test
    public void writeImmutableJsonArrayWritesExpectedForMixedArray() throws IOException {
        final String expectedString
                = "86" // Array of size 6
                + "F4" // false
                + "F6" // null
                + "03" // unsigned 3
                + "39032E" // -0815
                + "68" // Text of length 8
                + "6D79737472696E67" // "mystring
                + "A1616B6176"; // Object: {"k":"v"}

        final List<JsonValue> jsonValues = new ArrayList<>();
        jsonValues.add(newValue(false));
        jsonValues.add(newValue(null));
        jsonValues.add(newValue(3));
        jsonValues.add(newValue(-815));
        jsonValues.add(newValue("mystring"));
        jsonValues.add(newObject("{\"k\":\"v\"}"));

        final JsonArray underTest = (JsonArray) JsonValue.of(jsonValues);

        assertThat(CborTestUtils.serializeToHexString(underTest)).isEqualToIgnoringCase(expectedString);
    }

    @Test
    public void validateImmutableJsonArrayInternalCachingBehaviour() throws IOException {
        final JsonArray arrayWithSelfGeneratedCache = JsonFactory.newArrayBuilder(KNOWN_INT_VALUE_LIST).build();
        assertInternalCachesAreAsExpected(arrayWithSelfGeneratedCache, true, false);

        final ByteBuffer byteBuffer = cborFactory.toByteBuffer(arrayWithSelfGeneratedCache);
        final JsonArray arrayWithCborCache = cborFactory.readFrom(byteBuffer).asArray();
        assertInternalCachesAreAsExpected(arrayWithSelfGeneratedCache, true, false);
        final JsonArray arrayWithJsonCache = JsonFactory.newArray(arrayWithSelfGeneratedCache.toString());
        assertInternalCachesAreAsExpected(arrayWithSelfGeneratedCache, true, true);

        assertInternalCachesAreAsExpected(arrayWithCborCache, true, false);
        assertInternalCachesAreAsExpected(arrayWithJsonCache, false, true);
    }

    private void assertInternalCachesAreAsExpected(final JsonObject jsonObject, final boolean cborExpected,
            final boolean jsonExpected) {
        try {
            final Field valueListField = jsonObject.getClass().getDeclaredField("fieldMap");
            valueListField.setAccessible(true);
            final Object fieldMap = valueListField.get(jsonObject);

            final Field cborObjectField = fieldMap.getClass().getDeclaredField("cborObjectRepresentation");
            cborObjectField.setAccessible(true);
            byte[] cborObject = (byte[]) cborObjectField.get(fieldMap);

            final Field jsonStringField = fieldMap.getClass().getDeclaredField("jsonObjectStringRepresentation");
            jsonStringField.setAccessible(true);
            String jsonString = (String) jsonStringField.get(fieldMap);

            assertThat(cborObject != null).isEqualTo(cborExpected);
            assertThat(jsonString != null).isEqualTo(jsonExpected);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            System.err.println(
                    "Failed to access internal caching fields in JsonObject using reflection. " +
                            "This might just be a bug in the test."
            );
            e.printStackTrace();
        }
    }

    private void assertInternalCachesAreAsExpected(final JsonArray jsonArray, final boolean cborExpected,
            final boolean jsonExpected) {
        try {
            final Field valueListField = jsonArray.getClass().getDeclaredField("valueList");
            valueListField.setAccessible(true);
            final Object valueList = valueListField.get(jsonArray);

            final Field cborArrayField = valueList.getClass().getDeclaredField("cborArrayRepresentation");
            cborArrayField.setAccessible(true);
            byte[] cborArray = (byte[]) cborArrayField.get(valueList);

            final Field jsonStringField = valueList.getClass().getDeclaredField("jsonArrayStringRepresentation");
            jsonStringField.setAccessible(true);
            String jsonString = (String) jsonStringField.get(valueList);

            assertThat(cborArray != null).isEqualTo(cborExpected);
            assertThat(jsonString != null).isEqualTo(jsonExpected);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            System.err.println(
                    "Failed to access internal caching fields in JsonArray using reflection. " +
                            "This might just be a bug in the test."
            );
            e.printStackTrace();
        }
    }

}
