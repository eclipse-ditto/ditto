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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import com.eclipsesource.json.Json;

/**
 * Unit test for {@link JsonFactory}.
 */
public final class JsonFactoryTest {

    private static final String KNOWN_JSON_OBJECT_STRING =
            "{\"featureId\": \"" + 1 + "\", " + "\"functionblock\": null, " + "\"properties\": {" + "\"someInt\": 42,"
                    + "\"someString\": \"foo\"," + "\"someBool\": false," + "\"someObj\": {\"aKey\": \"aValue\"}" +
                    "}" + "}";

    private static final String KNOWN_JSON_ARRAY_STRING = "[\"one\",\"two\",\"three\"]";


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
        assertThat(jsonString.toString()).isEqualTo(expectedJsonString);
    }


    @Test
    public void nullLiteralReturnsExpected() {
        final JsonValue underTest = JsonFactory.nullLiteral();

        DittoJsonAssertions.assertThat(underTest).isNullLiteral();
    }


    @Test
    public void newValueFromTrueReturnsExpected() {
        final JsonValue underTest = JsonFactory.newValue(true);

        DittoJsonAssertions.assertThat(underTest).isBoolean();
        assertThat(underTest.asBoolean()).isTrue();
    }


    @Test
    public void newValueFromFalseReturnsExpected() {
        final JsonValue underTest = JsonFactory.newValue(false);

        DittoJsonAssertions.assertThat(underTest).isBoolean();
        assertThat(underTest.asBoolean()).isFalse();
    }


    @Test
    public void newValueFromIntReturnsExpected() {
        final int intValue = 42;
        final JsonValue underTest = JsonFactory.newValue(intValue);

        DittoJsonAssertions.assertThat(underTest).isNumber();
        assertThat(underTest.asInt()).isEqualTo(intValue);
    }


    @Test
    public void newValueFromLongReturnsExpected() {
        final long longValue = 1337L;
        final JsonValue underTest = JsonFactory.newValue(longValue);

        DittoJsonAssertions.assertThat(underTest).isNumber();
        assertThat(underTest.asLong()).isEqualTo(longValue);
    }


    @Test
    public void newValueFromDoubleReturnsExpected() {
        final double doubleValue = 23.7D;
        final JsonValue underTest = JsonFactory.newValue(doubleValue);

        DittoJsonAssertions.assertThat(underTest).isNumber();
        assertThat(underTest.asDouble()).isEqualTo(doubleValue);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectBuilderFromNullObject() {
        JsonFactory.newObjectBuilder(null);
    }


    @Test
    public void newObjectReturnsExpected() {
        final JsonObject underTest = JsonFactory.newObject();

        DittoJsonAssertions.assertThat(underTest).isObject();
        DittoJsonAssertions.assertThat(underTest).isEmpty();
        DittoJsonAssertions.assertThat(underTest).isNotNullLiteral();
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

        DittoJsonAssertions.assertThat(underTest).hasSize(fields.size()).contains(key1, value1).contains(key2, value2);
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

        DittoJsonAssertions.assertThat(underTest).isObject();
        DittoJsonAssertions.assertThat(underTest).isNotNullLiteral();
        DittoJsonAssertions.assertThat(underTest).isNotEmpty();
        DittoJsonAssertions.assertThat(underTest).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(underTest).contains(JsonFactory.newKey("featureId"), "1");
        DittoJsonAssertions.assertThat(underTest)
                .contains(JsonFactory.newKey("functionblock"), JsonFactory.nullLiteral());

        JsonObject expectedProperties = JsonFactory.newObject();
        expectedProperties = expectedProperties.setValue("someInt", 42);
        expectedProperties = expectedProperties.setValue("someString", "foo");
        expectedProperties = expectedProperties.setValue("someBool", false);

        JsonObject someObject = JsonFactory.newObject();
        someObject = someObject.setValue("aKey", "aValue");
        expectedProperties = expectedProperties.setValue("someObj", someObject);

        DittoJsonAssertions.assertThat(underTest).contains(JsonFactory.newKey("properties"), expectedProperties);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNewObjectBuilderFromNullIterable() {
        JsonFactory.newArrayBuilder(null);
    }


    @Test
    public void createNewEmptyArray() {
        final JsonArray underTest = JsonFactory.newArray();

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat(underTest).isEmpty();
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
        final String jsonString = KNOWN_JSON_ARRAY_STRING;
        final JsonArray underTest = JsonFactory.newArray(jsonString);
        final byte expectedSize = 3;

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat(underTest).isNotNullLiteral();
        DittoJsonAssertions.assertThat(underTest).isNotEmpty();
        DittoJsonAssertions.assertThat(underTest).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(underTest).isNotObject();
        DittoJsonAssertions.assertThat(underTest).contains("one");
        DittoJsonAssertions.assertThat(underTest).contains("two");
        DittoJsonAssertions.assertThat(underTest).contains("three");
    }


    @Test
    public void convertMinimalJsonNullReturnsNull() {
        final com.eclipsesource.json.JsonValue minimalJsonValue = null;
        final JsonValue underTest = JsonFactory.convert(minimalJsonValue);

        DittoJsonAssertions.assertThat(underTest).isNull();
    }


    @Test
    public void convertMinimalJsonObjectReturnsExpected() {
        final com.eclipsesource.json.JsonValue parsedJsonObjectString = Json.parse(KNOWN_JSON_OBJECT_STRING);
        final com.eclipsesource.json.JsonObject minimalJsonObject = parsedJsonObjectString.asObject();

        final JsonValue underTest = JsonFactory.convert(minimalJsonObject);
        final byte expectedSize = 3;

        DittoJsonAssertions.assertThat(underTest).isObject();
        DittoJsonAssertions.assertThat((JsonObject) underTest).hasSize(expectedSize);
    }


    @Test
    public void convertMinimalJsonStringReturnsExpected() {
        final String javaString = "summer";
        final com.eclipsesource.json.JsonValue minimalJsonString = Json.value(javaString);
        final JsonValue underTest = JsonFactory.convert(minimalJsonString);

        DittoJsonAssertions.assertThat(underTest).isString();
        assertThat(underTest.asString()).isEqualTo(javaString);
    }


    @Test
    public void convertMinimalJsonArrayReturnsExpected() {
        final com.eclipsesource.json.JsonValue parsedJsonArrayString = Json.parse(KNOWN_JSON_ARRAY_STRING);
        final com.eclipsesource.json.JsonArray minimalJsonArray = parsedJsonArrayString.asArray();

        final JsonValue underTest = JsonFactory.convert(minimalJsonArray);
        final byte expectedSize = 3;

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat((JsonArray) underTest).hasSize(expectedSize);
    }


    @Test
    public void convertMinimalJsonBooleanReturnsExpected() {
        final com.eclipsesource.json.JsonValue minimalJsonBoolean = Json.FALSE;
        final JsonValue underTest = JsonFactory.convert(minimalJsonBoolean);

        DittoJsonAssertions.assertThat(underTest).isBoolean();
        assertThat(underTest.asBoolean()).isFalse();
    }


    @Test
    public void convertMinimalJsonNullLiteralReturnsExpected() {
        final com.eclipsesource.json.JsonValue minimalJsonNullLiteral = Json.NULL;
        final JsonValue underTest = JsonFactory.convert(minimalJsonNullLiteral);

        DittoJsonAssertions.assertThat(underTest).isNullLiteral();
    }


    @Test
    public void convertMinimalJsonNumberReturnsExpected() {
        final double numberValue = 23.42D;
        final com.eclipsesource.json.JsonValue minimalJsonNumber = Json.value(numberValue);
        final JsonValue underTest = JsonFactory.convert(minimalJsonNumber);

        DittoJsonAssertions.assertThat(underTest).isNumber();
        assertThat(underTest.asDouble()).isEqualTo(numberValue);
    }


    @Test
    public void convertNullReturnsNull() {
        final JsonValue jsonValue = null;
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonValue);

        assertThat(underTest).isNull();
    }


    @Test
    public void convertObjectToMinimalJsonObject() {
        final JsonObject jsonObject = JsonFactory.newObject(KNOWN_JSON_OBJECT_STRING);
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonObject);

        assertThat(underTest.isObject()).isTrue();

        final com.eclipsesource.json.JsonObject underTestAsObject = (com.eclipsesource.json.JsonObject) underTest;

        assertThat(underTestAsObject.getString("featureId", "-1")).isEqualTo("1");
        assertThat(underTestAsObject.get("functionblock")).isEqualTo(Json.NULL);

        final com.eclipsesource.json.JsonObject expectedProperties = new com.eclipsesource.json.JsonObject();
        expectedProperties.add("someInt", 42).add("someString", "foo").add("someBool", false);
        expectedProperties.add("someObj", new com.eclipsesource.json.JsonObject().add("aKey", "aValue"));

        assertThat(underTestAsObject.get("properties")).isEqualTo(expectedProperties);
    }


    @Test
    public void convertJsonStringToMinimalJsonString() {
        final String stringValue = "winter";
        final JsonValue jsonString = JsonFactory.newValue(stringValue);
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonString);

        assertThat(underTest.isString()).isTrue();
        assertThat(underTest.asString()).isEqualTo(stringValue);
    }


    @Test
    public void convertArrayToMinimalJsonArray() {
        final JsonArray jsonArray = JsonFactory.newArray(KNOWN_JSON_ARRAY_STRING);
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonArray);

        assertThat(underTest.isArray()).isTrue();

        @SuppressWarnings("unchecked")
        final Iterable<com.eclipsesource.json.JsonValue> underTestAsArray =
                (Iterable<com.eclipsesource.json.JsonValue>) underTest;
        final byte expectedSize = 3;

        assertThat(underTestAsArray).hasSize(expectedSize);
        assertThat(underTestAsArray).containsSequence(Json.value("one"), Json.value("two"), Json.value("three"));
    }


    @Test
    public void convertJsonBooleanToMinimalJsonBoolean() {
        final JsonValue jsonBoolean = JsonFactory.newValue(true);
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonBoolean);

        assertThat(underTest.isBoolean()).isTrue();
        assertThat(underTest.asBoolean()).isTrue();
    }


    @Test
    public void convertJsonNullLiteralToMinimalJsonNullLiteral() {
        final JsonValue jsonNullLiteral = JsonFactory.nullLiteral();
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonNullLiteral);

        assertThat(underTest.isNull()).isTrue();
    }


    @Test
    public void convertJsonNumberToMinimalJsonNumber() {
        final double numberValue = 23.42D;
        final JsonValue jsonNumber = JsonFactory.newValue(numberValue);
        final com.eclipsesource.json.JsonValue underTest = JsonFactory.convert(jsonNumber);

        assertThat(underTest.isNumber()).isTrue();
        assertThat(underTest.asDouble()).isEqualTo(numberValue);
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

        DittoJsonAssertions.assertThat(actual).isEqualTo(expected);
    }


    @Test
    public void readFromJsonPrimitiveString() {
        final JsonValue expected = JsonFactory.newValue("foo");
        final JsonValue actual = JsonFactory.readFrom("\"foo\"");

        DittoJsonAssertions.assertThat(actual).isEqualTo(expected);
    }


    @Test(expected = NullPointerException.class)
    public void tryToReadFromNullReader() {
        JsonFactory.readFrom((Reader) null);
    }


    @Test
    public void readFromJsonArrayStringReader() {
        final StringReader stringReader = new StringReader(KNOWN_JSON_ARRAY_STRING);
        final JsonValue jsonValue = JsonFactory.readFrom(stringReader);

        DittoJsonAssertions.assertThat(jsonValue).isNotNull();
        DittoJsonAssertions.assertThat(jsonValue).isArray();
        DittoJsonAssertions.assertThat((JsonArray) jsonValue).contains("two");
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

        assertThat(fieldSelector.isEmpty());
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

        Assertions.assertThat(fieldSelector).isEmpty();
    }


    @Test
    public void newFieldSelectorFromEmptyStringIsEmpty() {
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        Assertions.assertThat(fieldSelector).isEmpty();
    }


    @Test
    public void newFieldDefinitionWithDifferentKeyArgumentsYieldsEqualObject() {
        final CharSequence charSequence = "myField";
        final JsonKey key = JsonFactory.newKey(charSequence);
        final JsonPointer pointer = JsonFactory.newPointer(key);

        final Class<?> valueType = int.class;
        final JsonFieldMarker markerMock = mock(JsonFieldMarker.class);

        final JsonFieldDefinition definition1 = JsonFactory.newFieldDefinition(charSequence, valueType, markerMock);
        final JsonFieldDefinition definition2 = JsonFactory.newFieldDefinition(key, valueType, markerMock);
        final JsonFieldDefinition definition3 = JsonFactory.newFieldDefinition(pointer, valueType, markerMock);

        assertThat(definition1).isEqualTo(definition2).isEqualTo(definition3);
    }


    @Test
    public void convertLongJsonValueToMinimalJsonValue() {
        final long longValue = System.currentTimeMillis();
        final JsonValue jsonValue = JsonFactory.newValue(longValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(longValue));
    }


    @Test
    public void convertMaxLongJsonValueToMinimalJsonValue() {
        final long longValue = Long.MAX_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(longValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(longValue));
    }


    @Test
    public void convertMinLongJsonValueToMinimalJsonValue() {
        final long longValue = Long.MIN_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(longValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(longValue));
    }


    @Test
    public void convertIntegerAsDoubleJsonValueToMinimalJsonValue() {
        final double doubleValue = 1.449662035141E12D;
        final JsonValue jsonValue = JsonFactory.newValue(doubleValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);


        assertThat(minimalJsonValue.toString()).isEqualTo("1449662035141");
    }


    @Test
    public void convertPureDoubleJsonValueToMinimalJsonValue() {
        final double doubleValue = 42.23D;
        final JsonValue jsonValue = JsonFactory.newValue(doubleValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(doubleValue));
    }


    @Test
    public void convertMaxDoubleJsonValueToMinimalJsonValue() {
        final double doubleValue = Double.MAX_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(doubleValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(doubleValue));
    }


    @Test
    public void convertMinDoubleJsonValueToMinimalJsonValue() {
        final double doubleValue = Double.MIN_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(doubleValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(doubleValue));
    }


    @Test
    public void convertMaxIntegerJsonValueToMinimalJsonValue() {
        final int intValue = Integer.MAX_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(intValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(intValue));
    }


    @Test
    public void convertMinIntegerJsonValueToMinimalJsonValue() {
        final int intValue = Integer.MIN_VALUE;
        final JsonValue jsonValue = JsonFactory.newValue(intValue);
        final com.eclipsesource.json.JsonValue minimalJsonValue = JsonFactory.convert(jsonValue);

        assertThat(minimalJsonValue.toString()).isEqualTo(String.valueOf(intValue));
    }

}
