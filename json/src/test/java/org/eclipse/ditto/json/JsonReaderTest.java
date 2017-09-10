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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.function.Function;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link JsonReader}.
 */
public final class JsonReaderTest {

    private static final JsonKey KNOWN_JSON_KEY = JsonFactory.newKey("myJsonKey");
    private static final JsonObject EMPTY_JSON_OBJECT = JsonFactory.newObject();
    private static final int KNOWN_INT_VALUE = 23;
    private static final String KNOWN_STRING_VALUE = "Wurst";
    private static final long KNOWN_LONG_VALUE = 42L;
    private static final double KNOWN_DOUBLE_VALUE = 1337.0D;
    private static final boolean KNOWN_BOOLEAN_VALUE = true;
    private static final JsonObject KNOWN_JSON_OBJECT = JsonFactory.newObject("{\"foo\":\"bar\"}");
    private static final JsonArray KNOWN_JSON_ARRAY = JsonFactory.newArray("[\"foo\",\"bar\",\"baz\"]");

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonReader.class, //
                areImmutable(), //
                provided(JsonObject.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(JsonReader.class) //
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonObject() {
        JsonReader.from(null);
    }

    /** */
    @Test
    public void tryToGetValueAsStringForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsString);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsStringForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalString);
    }

    /** */
    @Test
    public void tryToGetValueAsStringForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsString, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getOptionalValueAsStringForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalString, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsStringWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_INT_VALUE))::getAsString, KNOWN_JSON_KEY,
                "string");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsStringWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_INT_VALUE))::getAsOptionalString,
                KNOWN_JSON_KEY, "string");
    }

    /** */
    @Test
    public void getAsStringReturnsExpected() {
        final String actualValue =
                newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE)).getAsString(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_STRING_VALUE);
    }

    /** */
    @Test
    public void getAsOptionalStringReturnsExpected() {
        final Optional<String> optionalValue = newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))
                .getAsOptionalString(KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_STRING_VALUE);
    }

    /** */
    @Test
    public void tryToGetValueAsIntForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsInt);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsIntForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalInt);
    }

    /** */
    @Test
    public void tryToGetValueAsIntForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsInt, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getOptionalValueAsIntForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalInt, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsIntWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsInt, KNOWN_JSON_KEY, "int");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsIntWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalInt, KNOWN_JSON_KEY,
                "int");
    }

    /** */
    @Test
    public void getAsIntReturnsExpected() {
        final int actualValue = newReaderForValue(JsonFactory.newValue(KNOWN_INT_VALUE)).getAsInt(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_INT_VALUE);
    }

    /** */
    @Test
    public void getAsOptionalIntReturnsExpected() {
        final Optional<Integer> optionalValue =
                newReaderForValue(JsonFactory.newValue(KNOWN_INT_VALUE)).getAsOptionalInt(KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_INT_VALUE);
    }

    /** */
    @Test
    public void tryToGetValueAsLongForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsLong);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsLongForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalLong);
    }

    /** */
    @Test
    public void tryToGetValueAsLongForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsLong, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getOptionalValueAsLongForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalLong, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsLongWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsLong, KNOWN_JSON_KEY, "long");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsLongWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalLong, KNOWN_JSON_KEY,
                "long");
    }

    /** */
    @Test
    public void getAsLongReturnsExpected() {
        final long actualValue = newReaderForValue(JsonFactory.newValue(KNOWN_LONG_VALUE)).getAsLong(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_LONG_VALUE);
    }

    /** */
    @Test
    public void getAsOptionalLongReturnsExpected() {
        final Optional<Long> optionalValue = newReaderForValue(JsonFactory.newValue(KNOWN_LONG_VALUE)).getAsOptionalLong
                (KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_LONG_VALUE);
    }

    /** */
    @Test
    public void tryToGetValueAsDoubleForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsDouble);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsDoubleForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalDouble);
    }

    /** */
    @Test
    public void tryToGetValueAsDoubleForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsDouble, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getValueAsDoubleForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalDouble, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsDoubleWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsDouble, KNOWN_JSON_KEY,
                "double");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsDoubleWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalDouble, KNOWN_JSON_KEY,
                "double");
    }

    /** */
    @Test
    public void getAsDoubleReturnsExpected() {
        final double actualValue = newReaderForValue(JsonFactory.newValue(KNOWN_DOUBLE_VALUE)).getAsDouble(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_DOUBLE_VALUE);
    }

    /** */
    @Test
    public void getAsOptionalDoubleReturnsExpected() {
        final Optional<Double> optionalValue =
                newReaderForValue(JsonFactory.newValue(KNOWN_DOUBLE_VALUE)).getAsOptionalDouble(KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_DOUBLE_VALUE);
    }

    /** */
    @Test
    public void tryToGetValueAsBooleanForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsBoolean);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsBooleanForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalBoolean);
    }

    /** */
    @Test
    public void tryToGetValueAsBooleanForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsBoolean, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getOptionalValueAsBooleanForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalBoolean, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsBooleanWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsBoolean, KNOWN_JSON_KEY,
                "boolean");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsBooleanWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalBoolean, KNOWN_JSON_KEY,
                "boolean");
    }

    /** */
    @Test
    public void getAsBooleanReturnsExpected() {
        final boolean actualValue = newReaderForValue(JsonFactory.newValue(KNOWN_BOOLEAN_VALUE)).getAsBoolean(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_BOOLEAN_VALUE);
    }

    /** */
    @Test
    public void getAsOptionalBooleanReturnsExpected() {
        final Optional<Boolean> optionalValue =
                newReaderForValue(JsonFactory.newValue(KNOWN_BOOLEAN_VALUE)).getAsOptionalBoolean(KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_BOOLEAN_VALUE);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonObjectForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsJsonObject);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsJsonObjectForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalJsonObject);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonObjectForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsJsonObject, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getValueAsJsonObjectForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalJsonObject, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonObjectWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsJsonObject, KNOWN_JSON_KEY,
                "JSON object");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsJsonObjectWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalJsonObject, KNOWN_JSON_KEY,
                "JSON object");
    }

    /** */
    @Test
    public void getAsJsonObjectReturnsExpected() {
        final JsonObject actualValue = newReaderForValue(KNOWN_JSON_OBJECT).getAsJsonObject(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_JSON_OBJECT);
    }

    /** */
    @Test
    public void getAsOptionalJsonObjectReturnsExpected() {
        final Optional<JsonObject> optionalValue = newReaderForValue(KNOWN_JSON_OBJECT).getAsOptionalJsonObject
                (KNOWN_JSON_KEY);

        assertThat(optionalValue.orElse(null)).isEqualTo(KNOWN_JSON_OBJECT);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonArrayForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsJsonArray);
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsJsonArrayForNullKey() {
        expectNullPointerException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalJsonArray);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonArrayForAbsentKey() {
        expectJsonMissingFieldException(JsonReader.from(EMPTY_JSON_OBJECT)::getAsJsonArray, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void getOptionalValueAsJsonArrayForAbsentKey() {
        expectEmptyOptional(JsonReader.from(EMPTY_JSON_OBJECT)::getAsOptionalJsonArray, KNOWN_JSON_KEY);
    }

    /** */
    @Test
    public void tryToGetValueAsJsonArrayWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsJsonArray, KNOWN_JSON_KEY,
                "JSON array");
    }

    /** */
    @Test
    public void tryToGetOptionalValueAsJsonArrayWithWrongType() {
        expectJsonParseException(newReaderForValue(JsonFactory.newValue(KNOWN_STRING_VALUE))::getAsOptionalJsonArray, KNOWN_JSON_KEY,
                "JSON array");
    }

    /** */
    @Test
    public void getAsJsonArrayReturnsExpected() {
        final JsonArray actualValue = newReaderForValue(KNOWN_JSON_ARRAY).getAsJsonArray(KNOWN_JSON_KEY);

        assertThat(actualValue).isEqualTo(KNOWN_JSON_ARRAY);
    }

    /** */
    @Test
    public void getAsOptionalJsonArrayReturnsExpected() {
        final JsonObjectReader underTest = newReaderForValue(KNOWN_JSON_ARRAY);

        assertThat(underTest.getAsOptionalJsonArray(KNOWN_JSON_KEY)).contains(KNOWN_JSON_ARRAY);
    }

    /** */
    @Test
    public void readValueOfStringField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition("foo", String.class);
        final JsonObjectReader underTest = JsonReader.from(KNOWN_JSON_OBJECT);

        assertThat((String) underTest.get(fieldDefinition)).isEqualTo("bar");
    }

    /** */
    @Test
    public void readOptionalValueOfStringField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition("foo", String.class);
        final JsonObjectReader underTest = JsonReader.from(KNOWN_JSON_OBJECT);

        assertThat(underTest.getAsOptional(fieldDefinition)).contains("bar");
    }

    /** */
    @Test
    public void readOptionalValueOfStringFieldForAbsentKey() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition("foo", String.class);
        final JsonObjectReader underTest = JsonReader.from(EMPTY_JSON_OBJECT);

        assertThat(underTest.getAsOptional(fieldDefinition)).isEmpty();
    }

    /** */
    @Test
    public void readValueOfIntField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, int.class);
        final JsonObjectReader underTest = newReaderForValue(JsonFactory.newValue(KNOWN_INT_VALUE));

        assertThat((Integer) underTest.get(fieldDefinition)).isEqualTo(KNOWN_INT_VALUE);
    }

    /** */
    @Test
    public void readValueOfLongField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, long.class);
        final JsonObjectReader underTest = newReaderForValue(JsonFactory.newValue(KNOWN_LONG_VALUE));

        assertThat((Long) underTest.get(fieldDefinition)).isEqualTo(KNOWN_LONG_VALUE);
    }

    /** */
    @Test
    public void readValueOfDoubleField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, double.class);
        final JsonObjectReader underTest = newReaderForValue(JsonFactory.newValue(KNOWN_DOUBLE_VALUE));

        assertThat((Double) underTest.get(fieldDefinition)).isEqualTo(KNOWN_DOUBLE_VALUE);
    }

    /** */
    @Test
    public void readValueOfBooleanField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, boolean.class);
        final JsonObjectReader underTest = newReaderForValue(JsonFactory.newValue(KNOWN_BOOLEAN_VALUE));

        assertThat((Boolean) underTest.get(fieldDefinition)).isEqualTo(KNOWN_BOOLEAN_VALUE);
    }

    /** */
    @Test
    public void readValueOfJsonObjectField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, JsonObject.class);
        final JsonObjectReader underTest = newReaderForValue(KNOWN_JSON_OBJECT);

        assertThat((JsonObject) underTest.get(fieldDefinition)).isEqualTo(KNOWN_JSON_OBJECT);
    }

    /** */
    @Test
    public void readValueOfJsonArrayField() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, JsonArray.class);
        final JsonObjectReader underTest = newReaderForValue(KNOWN_JSON_ARRAY);

        assertThat((JsonArray) underTest.get(fieldDefinition)).isEqualTo(KNOWN_JSON_ARRAY);
    }

    @Test
    public void readNullValueAsString() {
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(KNOWN_JSON_KEY, String.class);
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(fieldDefinition, JsonFactory.nullLiteral())
                .build();
        final JsonObjectReader underTest = JsonReader.from(jsonObject);

        assertThat(underTest.<String>get(fieldDefinition)).isNull();
    }

    private static JsonObjectReader newReaderForValue(final JsonValue value) {
        final JsonObject jsonObject = newJsonObjectWithValue(value);
        return JsonReader.from(jsonObject);
    }

    private static JsonObject newJsonObjectWithValue(final JsonValue value) {
        return JsonFactory.newObjectBuilder().set(KNOWN_JSON_KEY, value).build();
    }

    private static void expectNullPointerException(final Function<JsonPointer, ?> getter) {
        try {
            getter.apply(null);
            fail("Expected a NullPointerException when trying to get a value for a null key.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    private static void expectJsonMissingFieldException(final Function<JsonPointer, ?> getter, final CharSequence key) {
        try {
            getter.apply(JsonFactory.newPointer(key));
            fail("Expected a JsonMissingFieldException when trying to get a value for an absent key.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(JsonMissingFieldException.class);
            assertThat(e).hasMessageContaining(key.toString());
        }
    }

    private static void expectJsonParseException(final Function<JsonPointer, ?> getter, final CharSequence key,
            final String expectedType) {
        try {
            getter.apply(JsonFactory.newPointer(key));
            fail("Expected a JsonParseException when trying to get a value with the wrong type.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(JsonParseException.class);
            assertThat(e).hasMessageContaining(key.toString());
            assertThat(e).hasMessageContaining(expectedType);
        }
    }

    private static void expectEmptyOptional(final Function<JsonPointer, Optional<?>> getter, final CharSequence key) {
        final Optional<?> result = getter.apply(JsonFactory.newPointer(key));

        assertThat(result).isEmpty();
    }

}
