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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Unit test for {@link JsonFactory}.
 */
public final class JsonFactoryTest {

    private static final String KNOWN_POINTER = "/functionblock/properties/someInt";

    private static final String KNOWN_JSON_OBJECT_STRING =
            "{\"featureId\": \"" + 1 + "\", " + "\"functionblock\": null, " + "\"properties\": {" + "\"someInt\": 42,"
                    + "\"someString\": \"foo\"," + "\"someBool\": false," + "\"someObj\": {\"aKey\": \"aValue\"}" +
                    "}" + "}";

    private static final String KNOWN_JSON_ARRAY_STRING = "[\"one\",\"two\",\"three\"]";
    private static final int KNOWN_INT = 42;
    private static final String KNOWN_STRING = "Hallo";
    private static final long KNOWN_LONG = 422308154711L;

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonFactory.class, areImmutable());
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetNewKeyFromNullString() {
        JsonFactory.newKey(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToGetNewKeyFromEmptyString() {
        JsonFactory.newKey("");
    }

    @Test
    public void newKeyFromAlreadyExistingKeyReturnsExistingKey() {
        final JsonKey oldJsonKey = JsonFactory.newKey("key");
        final JsonKey newJsonKey = JsonFactory.newKey(oldJsonKey);

        assertThat((Object) newJsonKey).isSameAs(oldJsonKey);
    }

    @Test
    public void newJsonStringFromNullReturnsNullValue() {
        final JsonValue jsonString = JsonFactory.newValue(null);

        assertThat(jsonString.isString()).isFalse();
        assertThat(jsonString.isNull()).isTrue();
    }

    @Test
    public void newJsonStringReturnsExpected() {
        final String string = "foo";
        final JsonValue jsonString = JsonFactory.newValue(string);
        final String expectedJsonString = "\"" + string + "\"";

        assertThat(jsonString.isString()).isTrue();
        assertThat(jsonString.toString()).hasToString(expectedJsonString);
    }

    @Test
    public void nullLiteralReturnsExpected() {
        final JsonValue underTest = JsonFactory.nullLiteral();

        assertThat(underTest).isNullLiteral();
    }

    @Test
    public void newValueFromTrueReturnsExpected() {
        final JsonValue underTest = JsonFactory.newValue(true);

        assertThat(underTest).isBoolean();
        assertThat(underTest.asBoolean()).isTrue();
    }

    @Test
    public void newValueFromFalseReturnsExpected() {
        final JsonValue underTest = JsonFactory.newValue(false);

        assertThat(underTest).isBoolean();
        assertThat(underTest.asBoolean()).isFalse();
    }

    @Test
    public void newValueFromIntReturnsExpected() {
        final int intValue = 42;
        final JsonValue underTest = JsonFactory.newValue(intValue);

        assertThat(underTest).isNumber();
        assertThat(underTest.asInt()).isEqualTo(intValue);
    }

    @Test
    public void newValueFromMaxIntReturnsExpected() {
        final int intValue = Integer.MAX_VALUE;
        final JsonValue underTest = JsonFactory.newValue(intValue);

        assertThat(underTest).isNumber();
        assertThat(underTest.asInt()).isEqualTo(intValue);
    }

    @Test
    public void newValueFromLongReturnsExpected() {
        final long longValue = 1337L;
        final JsonValue underTest = JsonFactory.newValue(longValue);

        assertThat(underTest).isNumber();
        assertThat(underTest.asLong()).isEqualTo(longValue);
    }

    @Test
    public void newValueFromMaxLongReturnsExpected() {
        final long longValue = Long.MAX_VALUE;
        final JsonValue underTest = JsonFactory.newValue(longValue);

        assertThat(underTest).isNumber();
        assertThat(underTest.asLong()).isEqualTo(longValue);
    }

    @Test
    public void newValueFromDoubleReturnsExpected() {
        final double doubleValue = 23.7D;
        final JsonValue underTest = JsonFactory.newValue(doubleValue);

        assertThat(underTest).isNumber();
        assertThat(underTest.asDouble()).isEqualTo(doubleValue);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectBuilderFromNullObject() {
        JsonFactory.newObjectBuilder(null);
    }

    @Test
    public void newObjectReturnsExpected() {
        final JsonObject underTest = JsonFactory.newObject();

        assertThat(underTest)
                .isObject()
                .isNotNullLiteral()
                .isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectFromNullMap() {
        JsonFactory.newObject((Map<JsonKey, JsonValue>) null);
    }

    @Test
    public void createNewObjectFromMap() {
        final JsonKey key1 = JsonFactory.newKey("key1");
        final JsonValue value1 = JsonFactory.newValue("value1");
        final JsonKey key2 = JsonFactory.newKey("key2");
        final JsonValue value2 = JsonFactory.newValue(false);
        final Map<JsonKey, JsonValue> fields = new LinkedHashMap<>();
        fields.put(key1, value1);
        fields.put(key2, value2);

        final JsonObject underTest = JsonFactory.newObject(fields);

        assertThat(underTest).hasSize(fields.size()).contains(key1, value1).contains(key2, value2);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectFromNullString() {
        JsonFactory.newObject((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateNewObjectFromEmptyString() {
        JsonFactory.newObject("");
    }

    @Test(expected = JsonParseException.class)
    public void tryToCreateNewObjectFromNonsenseString() {
        JsonFactory.newObject("Ma-O-Am");
    }

    @Test
    public void newObjectFromStringReturnsExpected() {
        final JsonObject underTest = JsonFactory.newObject(KNOWN_JSON_OBJECT_STRING);
        final byte expectedSize = 3;

        assertThat(underTest).isObject()
                .isNotEmpty()
                .isNotNullLiteral()
                .hasSize(expectedSize)
                .contains(JsonFactory.newKey("featureId"), "1")
                .contains(JsonFactory.newKey("functionblock"), JsonFactory.nullLiteral());

        JsonObject expectedProperties = JsonFactory.newObject();
        expectedProperties = expectedProperties.setValue("someInt", 42);
        expectedProperties = expectedProperties.setValue("someString", "foo");
        expectedProperties = expectedProperties.setValue("someBool", false);

        JsonObject someObject = JsonFactory.newObject();
        someObject = someObject.setValue("aKey", "aValue");
        expectedProperties = expectedProperties.setValue("someObj", someObject);

        assertThat(underTest).contains(JsonFactory.newKey("properties"), expectedProperties);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectBuilderFromNullIterable() {
        JsonFactory.newArrayBuilder(null);
    }

    @Test
    public void createNewEmptyArray() {
        final JsonArray underTest = JsonFactory.newArray();

        assertThat(underTest)
                .isArray()
                .isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateNewArrayFromEmptyString() {
        JsonFactory.newArray("");
    }

    @Test(expected = JsonParseException.class)
    public void tryToCreateNewArrayFromNonsenseString() {
        JsonFactory.newArray("Ma-O-Am");
    }

    @Test
    public void newArrayFromStringReturnsExpected() {
        final JsonArray underTest = JsonFactory.newArray(KNOWN_JSON_ARRAY_STRING);
        final byte expectedSize = 3;

        assertThat(underTest)
                .isArray()
                .isNotNullLiteral()
                .isNotEmpty()
                .hasSize(expectedSize)
                .isNotObject()
                .contains("one")
                .contains("two")
                .contains("three");
    }

    @Test(expected = JsonParseException.class)
    public void tryToReadJsonValueFromInvalidString() {
        JsonFactory.readFrom("{\"foo\":\"bar\"");
    }

    @Test(expected = JsonParseException.class)
    public void tryToReadJsonObjectFromInvalidInput() {
        JsonFactory.readFrom("\"42");
    }

    @Test
    public void readFromJsonObjectString() {
        final JsonValue expected = JsonFactory.newObject().setValue("foo", "bar");
        final JsonValue actual = JsonFactory.readFrom("{\"foo\":\"bar\"}");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void readFromJsonPrimitiveString() {
        final JsonValue expected = JsonFactory.newValue("foo");
        final JsonValue actual = JsonFactory.readFrom("\"foo\"");

        assertThat(actual).isEqualTo(expected);
    }

    @Test(expected = JsonParseException.class)
    public void tryToReadFromNullReader() {
        JsonFactory.readFrom((Reader) null);
    }

    @Test
    public void readFromJsonArrayStringReader() {
        final StringReader stringReader = new StringReader(KNOWN_JSON_ARRAY_STRING);
        final JsonValue jsonValue = JsonFactory.readFrom(stringReader);

        assertThat(jsonValue)
                .isNotNull()
                .isArray();
        assertThat((JsonArray) jsonValue).contains("two");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewFieldSelectorWithNullSet() {
        JsonFactory.newFieldSelector((Iterable<JsonPointer>) null);
    }

    @Test
    public void emptyFieldSelector() {
        final JsonFieldSelector actual = JsonFactory.emptyFieldSelector();

        assertThat(actual).isEmpty();
    }

    @Test
    public void newFieldSelectorBuilder() {
        final JsonFieldSelectorBuilder builder = JsonFactory.newFieldSelectorBuilder();

        // minimal check for correct build of empty selector
        assertThat(builder.build()).isEmpty();
    }

    @Test
    public void newFieldSelectorWithEmptySetReturnsEmptyFieldSelector() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(Collections.emptySet());

        assertThat(fieldSelector).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewFieldSelectorWithNullMandatoryPointer() {
        JsonFactory.newFieldSelector((JsonPointer) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewFieldSelectorWithNullFurtherPointers() {
        JsonFactory.newFieldSelector(mock(JsonPointer.class), null);
    }

    @Test
    public void newFieldSelectorFromPointersReturnsExpected() {
        final JsonPointer root = JsonFactory.newPointer("root");
        final JsonPointer child = JsonFactory.newPointer("root/child");
        final JsonPointer sibling = JsonFactory.newPointer("root/sibling");
        final JsonPointer sub = JsonFactory.newPointer("root/child/sub");
        final JsonPointer subsub = JsonFactory.newPointer("root/child/sub/subsub");

        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(root, child, sibling, sub, subsub);

        assertThat(fieldSelector.getPointers()).containsExactly(root, child, sibling, sub, subsub);
    }

    @Test
    public void newFieldSelectorFromPointerStringsReturnsExpected() {
        final String rootString = "rootString";
        final String childString = "rootString/childString";
        final String siblingString = "rootString/siblingString";
        final String subString = "rootString/childString/subString";
        final String subsubString = "rootString/childString/subString/subsubString";

        final JsonFieldSelector fieldSelector =
                JsonFactory.newFieldSelector(rootString, childString, siblingString, subString, subsubString);

        final JsonPointer root = JsonFactory.newPointer(rootString);
        final JsonPointer child = JsonFactory.newPointer(childString);
        final JsonPointer sibling = JsonFactory.newPointer(siblingString);
        final JsonPointer sub = JsonFactory.newPointer(subString);
        final JsonPointer subsub = JsonFactory.newPointer(subsubString);

        assertThat(fieldSelector.getPointers()).containsExactly(root, child, sibling, sub, subsub);
    }

    @Test
    public void newFieldSelectorFromNullStringIsEmpty() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector(null,
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(fieldSelector).isEmpty();
    }

    @Test
    public void newFieldSelectorFromEmptyStringIsEmpty() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(fieldSelector).isEmpty();
    }

    @Test
    public void getAppropriateValueForNullReturnsNullLiteral() {
        assertThat(JsonFactory.getAppropriateValue(null)).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void getAppropriateValueForJsonArrayReturnsJsonArray() {
        final JsonArray jsonArray = JsonFactory.newArrayBuilder()
                .add("foo", "bar")
                .add(1, 2)
                .add(true)
                .build();

        assertThat(JsonFactory.getAppropriateValue(jsonArray)).isEqualTo(jsonArray);
    }

    @Test
    public void getAppropriateValueForCharSequenceReturnsJsonStringValue() {
        final String stringValue = "Harambe";
        final JsonKey jsonKey = JsonFactory.newKey(stringValue);
        final JsonValue expected = JsonFactory.newValue(stringValue);

        assertThat(JsonFactory.getAppropriateValue(jsonKey)).isEqualTo(expected);
    }

    @Test
    public void getAppropriateValueForBoxedBooleanReturnsJsonBooleanValue() {
        final Boolean booleanValue = Boolean.TRUE;
        final JsonValue expected = JsonFactory.newValue(booleanValue);

        assertThat(JsonFactory.getAppropriateValue(booleanValue)).isEqualTo(expected);
    }

    @Test
    public void getAppropriateValueForBoxedDoubleReturnsJsonDoubleValue() {
        final Double doubleValue = Double.MAX_VALUE;
        final JsonValue expected = JsonFactory.newValue(doubleValue);

        assertThat(JsonFactory.getAppropriateValue(doubleValue)).isEqualTo(expected);
    }

    @Test
    public void getAppropriateValueForJsonObjectStringReturnsJsonStringValue() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set("foo", true)
                .set("/bar/baz", "Hallo")
                .build();
        final JsonValue expected = JsonFactory.newValue(jsonObject.toString());

        assertThat(JsonFactory.getAppropriateValue(jsonObject.toString())).isEqualTo(expected);
    }

    @Test
    public void newStringFieldDefinitionReturnsExpected() {
        final JsonFieldDefinition<String> underTest = JsonFactory.newStringFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(JsonFactory.newValue(KNOWN_STRING))).isEqualTo(KNOWN_STRING);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isNull();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_INT)))
                .withMessageContaining(String.valueOf(KNOWN_INT))
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("String")
                .withNoCause();
    }

    @Test
    public void newBooleanFieldDefinitionReturnsExpected() {
        final JsonFieldDefinition<Boolean> underTest = JsonFactory.newBooleanFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(JsonFactory.newValue(true))).isTrue();
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isNull();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_INT)))
                .withMessageContaining(String.valueOf(KNOWN_INT))
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("Boolean")
                .withNoCause();
    }

    @Test
    public void newIntFieldDefinitionReturnsExpected() {
        final JsonFieldDefinition<Integer> underTest = JsonFactory.newIntFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(JsonFactory.newValue(KNOWN_INT))).isEqualTo(KNOWN_INT);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isNull();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_STRING)))
                .withMessageContaining(KNOWN_STRING)
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("Integer")
                .withNoCause();
    }

    @Test
    public void newLongFieldDefinitionReturnsExpected() {
        final JsonFieldDefinition<Long> underTest = JsonFactory.newLongFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(JsonFactory.newValue(KNOWN_LONG))).isEqualTo(KNOWN_LONG);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isNull();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(false)))
                .withMessageContaining(String.valueOf("false"))
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("Long")
                .withNoCause();
    }

    @Test
    public void newDoubleFieldDefinitionReturnsExpected() {
        final double doubleValue = 23.424711D;
        final JsonFieldDefinition<Double> underTest = JsonFactory.newDoubleFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(JsonFactory.newValue(doubleValue))).isEqualTo(doubleValue);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isNull();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_STRING)))
                .withMessageContaining(KNOWN_STRING)
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("Double")
                .withNoCause();
    }

    @Test
    public void newJsonArrayFieldDefinitionReturnsExpected() {
        final JsonArray jsonArray = JsonFactory.newArray(KNOWN_JSON_ARRAY_STRING);
        final JsonObject jsonObject = JsonFactory.newObject(KNOWN_JSON_OBJECT_STRING);

        final JsonFieldDefinition<JsonArray> underTest = JsonFactory.newJsonArrayFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(jsonArray)).isEqualTo(jsonArray);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isEqualTo(JsonFactory.nullLiteral());
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_STRING)))
                .withMessageContaining(KNOWN_STRING)
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("JsonArray")
                .withNoCause();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(jsonObject))
                .withMessageContaining(jsonObject.toString())
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("JsonArray")
                .withNoCause();
    }

    @Test
    public void newJsonObjectFieldDefinitionReturnsExpected() {
        final JsonObject jsonObject = JsonFactory.newObject(KNOWN_JSON_OBJECT_STRING);
        final JsonArray jsonArray = JsonFactory.newArray(KNOWN_JSON_ARRAY_STRING);

        final JsonFieldDefinition<JsonObject> underTest = JsonFactory.newJsonObjectFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(jsonObject)).isEqualTo(jsonObject);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isEqualTo(JsonFactory.nullLiteral());
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(JsonFactory.newValue(KNOWN_STRING)))
                .withMessageContaining(KNOWN_STRING)
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("JsonObject")
                .withNoCause();
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.mapValue(jsonArray))
                .withMessageContaining(jsonArray.toString())
                .withMessageContaining(KNOWN_POINTER)
                .withMessageContaining("JsonObject")
                .withNoCause();
    }

    @Test
    public void newJsonValueFieldDefinitionReturnsExpected() {
        final JsonValue jsonValue = JsonFactory.newValue(KNOWN_LONG);
        final JsonObject jsonObject = JsonFactory.newObject(KNOWN_JSON_OBJECT_STRING);

        final JsonFieldDefinition<JsonValue> underTest = JsonFactory.newJsonValueFieldDefinition(KNOWN_POINTER);

        assertThat(underTest.mapValue(jsonValue)).isEqualTo(jsonValue);
        assertThat(underTest.mapValue(JsonFactory.nullLiteral())).isEqualTo(JsonFactory.nullLiteral());
    }

    @Test
    public void newJsonObjectFromPathAndValue() {
        final JsonObject expected = JsonObject.newBuilder()
                .set("features", JsonObject.newBuilder()
                        .set("featureA", JsonObject.newBuilder()
                                .set("properties", JsonObject.newBuilder()
                                        .set("temperature",
                                                JsonObject.newBuilder().set("value", 23.0).set("unit", "°C").build())
                                        .build())
                                .build())
                        .build())
                .build();

        final JsonPointer path = JsonPointer.of("features/featureA/properties/temperature");
        final JsonValue value = JsonObject.newBuilder()
                .set("value", 23.0)
                .set("unit", "°C")
                .build();

        final JsonObject actual = JsonFactory.newObject(path, value);

        assertThat(actual).isEqualTo(expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void newJsonObjectThrowsExceptionIfRootIsNotAnObject() {
        JsonFactory.newObject(JsonPointer.empty(), JsonValue.of(1));
    }

}
