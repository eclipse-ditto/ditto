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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonObject}.
 */
@SuppressWarnings("ConstantConditions")
public final class ImmutableJsonObjectTest {

    private static final int KNOWN_INT_23 = 23;
    private static final int KNOWN_INT_42 = 42;
    private static final JsonKey KNOWN_KEY_FOO = JsonKey.of("foo");
    private static final JsonKey KNOWN_KEY_BAR = JsonKey.of("bar");
    private static final JsonKey KNOWN_KEY_BAZ = JsonKey.of("baz");
    private static final JsonValue KNOWN_VALUE_FOO = JsonValue.of("bar");
    private static final JsonValue KNOWN_VALUE_BAR = JsonValue.of("baz");
    private static final JsonValue KNOWN_VALUE_BAZ = JsonValue.of(KNOWN_INT_42);
    private static final Map<String, JsonField> KNOWN_FIELDS = new LinkedHashMap<>();
    private static final String KNOWN_JSON_STRING;

    static {
        KNOWN_FIELDS.put(KNOWN_KEY_FOO.toString(), toField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        KNOWN_FIELDS.put(KNOWN_KEY_BAR.toString(), toField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        KNOWN_FIELDS.put(KNOWN_KEY_BAZ.toString(), toField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        KNOWN_JSON_STRING = "{"
                + "\"" + KNOWN_KEY_FOO + "\":\"" + KNOWN_VALUE_FOO.asString() + "\","
                + "\"" + KNOWN_KEY_BAR + "\":\"" + KNOWN_VALUE_BAR.asString() + "\","
                + "\"" + KNOWN_KEY_BAZ + "\":" + KNOWN_VALUE_BAZ.asInt()
                + "}";
    }

    private static Map<String, JsonField> toMap(final CharSequence keyName, final int rawValue) {
        return toMap(keyName, JsonValue.of(rawValue));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonObject.class,
                areImmutable(),
                provided(ImmutableJsonObject.SoftReferencedFieldMap.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final Map<String, JsonField> jsonFieldsRed = toMap("foo", JsonValue.of(1));
        final Map<String, JsonField> jsonFieldsBlack = toMap("foo", JsonValue.of(2));
        final ImmutableJsonObject.SoftReferencedFieldMap
                redFieldMap = ImmutableJsonObject.SoftReferencedFieldMap.of(jsonFieldsRed);
        final ImmutableJsonObject.SoftReferencedFieldMap
                blackFieldMap = ImmutableJsonObject.SoftReferencedFieldMap
                .of(jsonFieldsBlack);
        final ImmutableJsonObject redObject = ImmutableJsonObject.of(jsonFieldsRed);
        final ImmutableJsonObject blackObject = ImmutableJsonObject.of(jsonFieldsBlack);

        EqualsVerifier.forClass(ImmutableJsonObject.class)
                .withPrefabValues(ImmutableJsonObject.SoftReferencedFieldMap.class, redFieldMap, blackFieldMap)
                .withPrefabValues(ImmutableJsonObject.class, redObject, blackObject)
                .withNonnullFields("fieldMap")
                .verify();
    }

    @Test
    public void orderOfFieldsDoesNotAffectEquality() {
        final JsonObjectBuilder barJsonObjectBuilder = JsonObject.newBuilder();
        barJsonObjectBuilder.set("baz", KNOWN_INT_23);

        final JsonObjectBuilder fooJsonObjectLeftBuilder = JsonObject.newBuilder();
        fooJsonObjectLeftBuilder.set("bar", barJsonObjectBuilder.build());
        final int intValue10 = 10;
        fooJsonObjectLeftBuilder.set("yo", intValue10);

        final JsonObjectBuilder leftObjectBuilder = JsonObject.newBuilder();
        leftObjectBuilder.set("foo", fooJsonObjectLeftBuilder.build());

        final JsonObjectBuilder fooJsonObjectRightBuilder = JsonObject.newBuilder();
        fooJsonObjectRightBuilder.set("yo", intValue10);
        fooJsonObjectRightBuilder.set("bar", barJsonObjectBuilder.build());

        final JsonObjectBuilder rightObjectBuilder = JsonObject.newBuilder();
        rightObjectBuilder.set("foo", fooJsonObjectRightBuilder.build());

        final JsonObject leftJsonObject = leftObjectBuilder.build();
        final JsonObject rightJsonObject = rightObjectBuilder.build();

        assertThat(leftJsonObject).isEqualTo(rightJsonObject);
    }

    @Test
    public void twoParsedObjectsFromSameStringHaveSameHashCode() {
        final String jsonObjectString =
                "{\"location\":{\"latitude\":44.673856, \"longitude\":8.261719},\"maker\":\"ACME\"}";
        final Function<String, JsonValue> parser = JsonValueParser.fromString();

        final JsonValue parsedFirst = parser.apply(jsonObjectString);
        final JsonValue parsedSecond = parser.apply(jsonObjectString);

        assertThat(parsedFirst).isInstanceOf(ImmutableJsonObject.class);
        assertThat(parsedSecond).isInstanceOf(ImmutableJsonObject.class);
        assertThat(parsedFirst.equals(parsedSecond)).isTrue();
        assertThat(parsedFirst.hashCode()).hasSameHashCodeAs(parsedSecond.hashCode());
    }

    @Test
    public void getEmptyInstanceReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isObject()
                .isEmpty();
        assertThat(underTest).hasSize(0);
        assertThat(underTest.asObject()).isSameAs(underTest);
        assertThat(underTest.toString()).hasToString("{}");
    }

    @Test
    public void objectIsNothingElse() {
        final JsonValue underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isObject()
                .isNotNullLiteral()
                .isNotBoolean()
                .isNotNumber()
                .isNotString()
                .isNotArray();
        assertThat(underTest.isInt()).isFalse();
        assertThat(underTest.isLong()).isFalse();
        assertThat(underTest.isDouble()).isFalse();
    }

    @Test
    public void checkUnsupportedOperations() {
        final JsonValue underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).doesNotSupport(JsonValue::asArray)
                .doesNotSupport(JsonValue::asBoolean)
                .doesNotSupport(JsonValue::asString)
                .doesNotSupport(JsonValue::asInt)
                .doesNotSupport(JsonValue::asLong)
                .doesNotSupport(JsonValue::asDouble);
    }

    @Test
    public void getInstanceReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).isObject()
                .isNotEmpty()
                .hasSize(3);
        assertThat(underTest.asObject()).isSameAs(underTest);
        assertThat(underTest.toString()).hasToString(KNOWN_JSON_STRING);
    }

    @Test
    public void getReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonKey key = JsonKey.of("foo");
        final JsonValue expectedValue = JsonValue.of("bar");

        assertThat(underTest).contains(key, expectedValue);
    }

    @Test
    public void setWorksAsExpected() {
        final JsonKey key = JsonKey.of("key");
        final JsonValue valueToAdd = JsonValue.of("oxi");

        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isEmpty();

        final JsonObject newJsonObject = underTest.setValue(key, valueToAdd);

        assertThat(underTest).isEmpty();
        assertThat(newJsonObject).isNotEmpty()
                .hasSize(1)
                .isNotSameAs(underTest)
                .contains(key, valueToAdd);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetIntValueWithNullKey() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setValue(null, KNOWN_INT_23);
    }

    @Test
    public void tryToSetIntValueWithEmptyKey() {
        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.setValue("", KNOWN_INT_23))
                .withMessage("The key or pointer must not be empty!")
                .withNoCause();
    }

    @Test
    public void setIntCreatesDisjointJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonKey.of("key");
        final int value = KNOWN_INT_42;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(1)
                .contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    @Test
    public void setExistingIntReturnsSameInstance() {
        final JsonKey key = KNOWN_KEY_FOO;
        final int value = KNOWN_INT_23;
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(key, JsonValue.of(value)));

        final JsonObject jsonObject = underTest.setValue(key, value);

        assertThat(jsonObject).isSameAs(underTest);
    }

    @Test
    public void setIntWithKeyContainingSlashes() {
        final CharSequence key = "foo/bar/baz";
        final JsonKey jsonKey = JsonKey.of(key);
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        final JsonObject jsonObjectWithIntValue = underTest.setValue(key, KNOWN_INT_42);

        assertThat(jsonObjectWithIntValue).contains(jsonKey, KNOWN_INT_42);
    }

    @Test
    public void setFieldTwiceReturnsSameInstance() {
        final JsonKey jsonKey = JsonKey.of("foo/bar/baz");
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        final JsonObject jsonObjectWithIntValue1 = underTest.setValue(jsonKey, KNOWN_INT_42);
        final JsonObject jsonObjectWithIntValue2 = jsonObjectWithIntValue1.setValue(jsonKey, KNOWN_INT_42);

        assertThat(jsonObjectWithIntValue2)
                .contains(jsonKey, KNOWN_INT_42)
                .isSameAs(jsonObjectWithIntValue1);
    }

    @Test
    public void setLongCreatesDisjointJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonKey.of("key");
        final long value = Long.MAX_VALUE;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(1)
                .contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    @Test
    public void setDoubleCreatesDisjointJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonKey.of("key");
        final double value = Double.MAX_VALUE;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(1)
                .contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    @Test
    public void setBooleanCreatesDisjointJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonKey.of("key");
        final boolean value = false;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(1)
                .contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    @Test
    public void setStringCreatesDisjoint() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonKey.of("key");
        final String value = "black out";
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(1)
                .contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetValueWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setValue((JsonPointer) null, mock(JsonValue.class));
    }

    @Test
    public void tryToSetValueWithEmptyJsonPointer() {
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.setValue(JsonPointer.empty(), KNOWN_INT_42))
                .withMessage("The key or pointer must not be empty!")
                .withNoCause();
    }

    @Test
    public void tryToSetValueWithSlashCharSequence() {
        final String slash = "/";
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.setValue(slash, KNOWN_VALUE_BAR))
                .withMessage("The key or pointer must not be empty!")
                .withNoCause();
    }

    @Test
    public void setValueWithSlashJsonKeyWorksAsExpected() {
        final JsonKey jsonKey = JsonKey.of("/");
        final JsonValue value = KNOWN_VALUE_BAR;
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        final JsonObject withValue = underTest.setValue(jsonKey, value);

        assertThat(withValue).hasSize(1).contains(jsonKey, value);
    }

    @Test
    public void setValueWithJsonPointerToEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonPointer jsonPointer = JsonPointer.of("foo/bar/baz");
        final JsonValue valueToAdd = JsonValue.of(KNOWN_INT_23);
        final JsonObject actual = underTest.setValue(jsonPointer, valueToAdd);

        /*
         * Expected JSON object after setting a value through a pointer:
         *
         * {
         *    "foo": {
         *       "bar": {
         *          "baz": 23
         *       }
         *    }
         * }
         */
        final JsonObjectBuilder bazJsonObjectBuilder = JsonObject.newBuilder();
        bazJsonObjectBuilder.set("baz", valueToAdd);

        final JsonObjectBuilder barJsonObjectBuilder = JsonObject.newBuilder();
        barJsonObjectBuilder.set("bar", bazJsonObjectBuilder.build());

        final JsonObjectBuilder fooJsonObjectBuilder = JsonObject.newBuilder();
        fooJsonObjectBuilder.set("foo", barJsonObjectBuilder.build());

        final JsonObject expected = fooJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void setValueWithJsonPointerToExistingJsonObject() {

        /*
         * JSON object before setting a value through a pointer:
         *
         * {
         *    "foo": {
         *       "bar": {
         *          "baz": 23
         *       },
         *       "yo": 10
         *    }
         * }
         */
        final JsonObject bazJsonObject = ImmutableJsonObject.of(toMap("baz", KNOWN_INT_23));

        final int intValue10 = 10;
        final JsonObject barJsonObject = JsonObject.newBuilder()
                .set("bar", bazJsonObject)
                .set("yo", intValue10)
                .build();

        final JsonObject underTest = ImmutableJsonObject.of(toMap("foo", barJsonObject));

        final JsonPointer jsonPointer = JsonPointer.of("foo/bar/allYourBase");
        final JsonValue valueToAdd = JsonValue.of("are belong to us!");
        final JsonObject actual = underTest.setValue(jsonPointer, valueToAdd);

        /*
         * Expected JSON object after setting a value through a pointer:
         *
         * {
         *    "foo": {
         *       "bar": {
         *          "baz": 23,
         *          "allYourBase:" "are belong to us!"
         *       },
         *       "yo": 10
         *    }
         * }
         */
        final JsonObjectBuilder bazJsonObjectBuilder = JsonObject.newBuilder();
        bazJsonObjectBuilder.set("baz", KNOWN_INT_23);
        bazJsonObjectBuilder.set("allYourBase", valueToAdd);

        final JsonObjectBuilder barJsonObjectBuilder = JsonObject.newBuilder();
        barJsonObjectBuilder.set("bar", bazJsonObjectBuilder.build());
        barJsonObjectBuilder.set("yo", intValue10);

        final JsonObjectBuilder fooJsonObjectBuilder = JsonObject.newBuilder();
        fooJsonObjectBuilder.set("foo", barJsonObjectBuilder.build());

        final JsonObject expected = fooJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddAllWithNullJsonFieldsIterable() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setAll(null);
    }

    @Test
    public void setAllWithEmptyJsonFieldIterableReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject sameAsBefore = underTest.setAll(Collections.emptyList());

        assertThat(sameAsBefore).isSameAs(underTest);
    }

    @Test
    public void setAllJsonFieldsWorksAsExpected() {
        final Collection<JsonField> jsonFieldsToAdd = new ArrayList<>();
        jsonFieldsToAdd.add(toField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        jsonFieldsToAdd.add(toField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        jsonFieldsToAdd.add(toField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        final JsonObject emptyJsonObject = ImmutableJsonObject.empty();
        final JsonObject underTest = emptyJsonObject.setAll(jsonFieldsToAdd);

        assertThat(underTest)
                .hasSize(jsonFieldsToAdd.size())
                .containsExactlyElementsOf(jsonFieldsToAdd);
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetValueByNullName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.getValue((String) null);
    }

    @Test
    public void getValueWithEmptyStringReturnsEmptyOptional() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.getValue("")).contains(underTest);
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetValueByNullKey() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.getValue((JsonKey) null);
    }

    @Test
    public void getExistingValueByName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.getValue(KNOWN_KEY_BAZ)).contains(KNOWN_VALUE_BAZ);
    }

    @Test
    public void getNonExistingValueByNameReturnsEmptyOptional() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.getValue("waldo")).isEmpty();
    }

    @Test
    public void getExistingValueByKeyReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).contains(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ);
    }

    @Test
    public void getValueWithPointerFromJsonObjectWithJsonArrayAtSomePointerLevel() {
        final JsonObject underTest = JsonObject.newBuilder()
                .set(KNOWN_KEY_FOO, KNOWN_VALUE_FOO)
                .set(KNOWN_KEY_BAR, JsonArray.newBuilder()
                        .add(KNOWN_INT_23)
                        .add("Morty")
                        .add(KNOWN_INT_42)
                        .build())
                .set(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ)
                .build();

        final Optional<JsonValue> value = underTest.getValue("/bar/Morty/oogle");

        assertThat(underTest).isInstanceOf(ImmutableJsonObject.class);
        assertThat(value).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveJsonFieldByNullName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.remove(null);
    }

    @Test
    public void removeNonExistingJsonFieldByNameReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove("schroedinger");

        assertThat(afterRemoval).isSameAs(underTest);
    }

    @Test
    public void removeNonExistingJsonFieldByKeyReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove(JsonKey.of("schroedinger"));

        assertThat(afterRemoval).isSameAs(underTest);
    }

    @Test
    public void removingExistingJsonFieldByNameReturnsDisjunctJsonObject() {
        final String nameToRemove = KNOWN_KEY_BAR.toString();
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove(nameToRemove);
        final int expectedSize = underTest.getSize() - 1;

        assertThat(afterRemoval).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .doesNotContain(KNOWN_KEY_BAR);

        underTest.remove(KNOWN_KEY_BAZ);

        assertThat(afterRemoval)
                .as("Another removal on original"
                        + " JSON object has no influence on the JSON object which was created after first removal.")
                .contains(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ);
    }

    @Test
    public void removingSubAttributesWorksAsExpected() {
        final Map<String, JsonField> jsonFields = new LinkedHashMap<>();
        jsonFields.put(KNOWN_KEY_FOO.toString(), toField(KNOWN_KEY_FOO, JsonValue.of(123)));
        jsonFields.put(KNOWN_KEY_BAR.toString(), toField(KNOWN_KEY_BAR, JsonValue.of(true)));
        jsonFields.put(KNOWN_KEY_BAZ.toString(), toField(KNOWN_KEY_BAZ, JsonObject.of("{\"bla\":\"blub\"}")));
        final JsonField complex = toField("complex", JsonObject.of("{\"subToDelete\":42}"));
        jsonFields.put(complex.getKeyName(), complex);

        final JsonObject underTest = ImmutableJsonObject.of(jsonFields);

        final JsonPointer jsonPointer = JsonPointer.of("complex/subToDelete");
        final JsonObject afterRemoval = underTest.remove(jsonPointer);
        jsonFields.remove(complex.getKeyName());
        jsonFields.put(complex.getKeyName(), toField(complex.getKey(), JsonObject.of("{}")));

        assertThat(afterRemoval).isNotSameAs(underTest)
                .doesNotContain(JsonKey.of("subToDelete"))
                .containsExactlyElementsOf(jsonFields.values());
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveJsonFieldByNullKey() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.remove((JsonKey) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.remove((JsonPointer) null);
    }

    @Test
    public void removeEmptyJsonPointerReturnsSameInstance() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject returned = underTest.remove(JsonPointer.empty());

        assertThat(returned).isSameAs(underTest);
    }

    @Test
    public void removeExistingValueByPointerReturnsExpected() {
        /*
         * JSON object before removing a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": 42
         *       }
         *    }
         * }
         */
        final JsonObject nestedJsonObject = ImmutableJsonObject.of(toMap("someNestedKey", KNOWN_INT_42));

        JsonObject attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", nestedJsonObject));

        final JsonObject underTest = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));

        final JsonPointer jsonPointer = JsonPointer.of("someObjectAttribute/someKey/someNestedKey");

        final JsonObject actual = underTest.remove(jsonPointer);

        /*
         * Expected JSON object after removing a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {}
         *    }
         * }
         */
        attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", JsonObject.empty()));

        final JsonObject expected = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getKeysReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonKey[] expectedKeys = new JsonKey[]{KNOWN_KEY_FOO, KNOWN_KEY_BAR, KNOWN_KEY_BAZ};
        final List<JsonKey> actualKeys = underTest.getKeys();

        assertThat(actualKeys).containsOnly(expectedKeys);
    }

    @Test
    public void setExistingFieldWithJsonPointerWorksAsExpected() {
        /*
         * JSON object before setting a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": 42
         *       }
         *    }
         * }
         */
        JsonObject nestedJsonObject = ImmutableJsonObject.of(toMap("someNestedKey", KNOWN_INT_42));
        JsonObject attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", nestedJsonObject));
        final JsonObject underTest = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));
        final JsonPointer jsonPointer = JsonPointer.of("someObjectAttribute/someKey/someNestedKey");

        final JsonValue valueToSet = JsonValue.of("monday");
        final JsonObject actual = underTest.setValue(jsonPointer, valueToSet);

        /*
         * Expected JSON object after setting a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": "monday"
         *       }
         *    }
         * }
         */
        nestedJsonObject = ImmutableJsonObject.of(toMap("someNestedKey", valueToSet));
        attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", nestedJsonObject));
        final JsonObject expected = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void setAttributeByPointerToExistingJsonObject() {
        /*
         * Existing JSON object:
         *
         * {
         *    "thingId":"998bed03-2350-473a-8c42-b9c5558cf8af",
         *    "attributes": {
         *       "manufacturer":"ACME",
         *       "make":"Fancy Fab Car",
         *       "model":"Environmental FourWheeler 4711",
         *       "VIN":"0815666337"
         *    },
         *    "features": {
         *       "Vehicle": {
         *          "featureId":"Vehicle",
         *          "functionblock":null,
         *          "properties": {
         *             "configuration": {
         *                "transmission": {
         *                   "type":"manual",
         *                   "gears":7
         *                }
         *             },
         *             "status": {
         *                "running":true,
         *                "speed":90,
         *                "gear":5
         *             },
         *             "fault": {
         *                "flatTyre":false
         *             }
         *          }
         *       },
         *       "EnvironmentScanner": {
         *          "featureId":"EnvironmentScanner",
         *          "functionblock":null,
         *          "properties": {
         *             "temperature":20.8,
         *             "humidity":73,
         *             "barometricPressure":970.7,
         *             "location": {
         *                "longitude":47.682170,
         *                "latitude":9.386372
         *             },
         *             "altitude":399
         *          }
         *       }
         *    }
         * }
         */

        JsonObject underTest = JsonObject.of("{\"thingId\":\"998bed03-2350-473a-8c42-b9c5558cf8af\","
                + "\"attributes\":{\"manufacturer\":\"ACME\",\"make\":\"Fancy Fab Car\","
                + "\"model\":\"Environmental FourWheeler 4711\",\"VIN\":\"0815666337\"},"
                + "\"features\":{\"Vehicle\":{\"featureId\":\"Vehicle\",\"functionblock\":null,"
                + "\"properties\":{\"configuration\":{\"transmission\":{\"type\":\"manual\",\"gears\":7}},"
                + "\"status\":{\"running\":true,\"speed\":90,\"gear\":5},\"fault\":{\"flatTyre\":false}}},"
                + "\"EnvironmentScanner\":{\"featureId\":\"EnvironmentScanner\",\"functionblock\":null,"
                + "\"properties\":{\"temperature\":20.8,\"humidity\":73,\"barometricPressure\":970.7,"
                + "\"location\":{\"longitude\":47.682170,\"latitude\":9.386372},\"altitude\":399}}}}");
        underTest = underTest.setValue(JsonPointer.of("/foo/bar/baz"), JsonObject.of("{\"alice\":\"bob\"}"));

        final JsonObjectBuilder bazObjectBuilder = JsonObject.newBuilder();
        bazObjectBuilder.set("alice", "bob");

        final JsonObjectBuilder barObjectBuilder = JsonObject.newBuilder();
        barObjectBuilder.set("baz", bazObjectBuilder.build());

        final JsonObjectBuilder fooObjectBuilder = JsonObject.newBuilder();
        fooObjectBuilder.set("bar", barObjectBuilder.build());
        final JsonObject expectedFoo = fooObjectBuilder.build();

        final Optional<JsonValue> jsonValueOptional = underTest.getValue("foo");

        assertThat(jsonValueOptional).contains(expectedFoo);
    }

    @Test
    public void setNonExistingFieldWithJsonPointerWorksAsExpected() {
        /*
         * JSON object before setting a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": 42
         *       }
         *    }
         * }
         */
        final JsonObject nestedJsonObject = ImmutableJsonObject.of(toMap("someNestedKey", KNOWN_INT_42));
        final JsonObject attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", nestedJsonObject));
        final JsonObject underTest = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));
        final JsonPointer jsonPointer = JsonPointer.of("someObjectAttribute/isGroovy");

        final JsonValue valueToSet = JsonValue.of(false);
        final JsonObject actual = underTest.setValue(jsonPointer, valueToSet);

        /*
         * Expected JSON object after setting a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": 42
         *       },
         *       "isGroovy": false
         *    }
         * }
         */

        final JsonObjectBuilder attributeJsonObjectBuilder = JsonObject.newBuilder();
        attributeJsonObjectBuilder.set("someKey", nestedJsonObject);
        attributeJsonObjectBuilder.set("isGroovy", valueToSet);

        final JsonObjectBuilder rootJsonObjectBuilder = JsonObject.newBuilder();
        rootJsonObjectBuilder.set("someObjectAttribute", attributeJsonObjectBuilder.build());

        final JsonObject expected = rootJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void setValueThroughPointerOnEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonPointer jsonPointer = JsonPointer.of("someObjectAttribute/someKey/someNestedKey");
        final JsonValue valueToSet = JsonValue.of("monday");
        final JsonObject actual = underTest.setValue(jsonPointer, valueToSet);

        /*
         * Expected JSON object after setting a value through a pointer:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": "monday"
         *       }
         *    }
         * }
         */
        final JsonObjectBuilder nestedJsonObjectBuilder = JsonObject.newBuilder();
        nestedJsonObjectBuilder.set("someNestedKey", valueToSet);

        final JsonObjectBuilder attributeJsonObjectBuilder = JsonObject.newBuilder();
        attributeJsonObjectBuilder.set("someKey", nestedJsonObjectBuilder.build());

        final JsonObjectBuilder rootJsonObjectBuilder = JsonObject.newBuilder();
        rootJsonObjectBuilder.set("someObjectAttribute", attributeJsonObjectBuilder.build());

        final JsonObject expected = rootJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void setValueThroughPointerOnNonEmptyJsonObject() {
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap("thingId", JsonValue.of("the_thing")));

        final JsonValue additionalJsonValue = JsonFactory.readFrom("{\"foo\":\"bar\"}");
        final JsonPointer pointer = JsonPointer.of("attributes/the_attribute");

        final JsonObject actual = underTest.setValue(pointer, additionalJsonValue);
        final Map<String, JsonField> jsonFieldMap = toMap("thingId", JsonValue.of("the_thing"));
        jsonFieldMap.put("attributes", toField("attributes", JsonObject.newBuilder()
                .set("the_attribute", JsonObject.of("{\"foo\":\"bar\"}"))
                .build()));
        final JsonObject expected = ImmutableJsonObject.of(jsonFieldMap);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void iteratorWorksAsExpected() {
        final JsonObject jsonObject = ImmutableJsonObject.of(KNOWN_FIELDS);
        final List<JsonField> expectedJsonFields = new ArrayList<>();
        expectedJsonFields.add(toField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        expectedJsonFields.add(toField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        expectedJsonFields.add(toField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        final Iterator<JsonField> underTest = jsonObject.iterator();
        int index = 0;
        while (underTest.hasNext()) {
            assertThat(index).isEqualTo(expectedJsonFields.indexOf(underTest.next()));
            index++;
        }
        final int actualSize = index;

        assertThat(actualSize).isEqualTo(expectedJsonFields.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void iteratorDoesNotAllowChangingTheJsonObject() {
        final JsonObject jsonObject = ImmutableJsonObject.of(KNOWN_FIELDS);

        final Iterator<JsonField> underTest = jsonObject.iterator();

        while (underTest.hasNext()) {
            underTest.next();
            underTest.remove();
        }
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallContainsWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.contains((JsonPointer) null);
    }

    @Test
    public void containsWithEmptyJsonPointerReturnsExpected() {
        final JsonPointer emptyPointer = JsonFactory.emptyPointer();
        ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        assertThat(underTest.contains(emptyPointer)).isFalse();

        final JsonKey key = JsonKey.of("/");
        underTest = ImmutableJsonObject.of(toMap(key, KNOWN_VALUE_BAR));

        assertThat(underTest.contains(emptyPointer)).isFalse();
    }

    @Test
    public void containsJsonPointerReturnsExpected() {
        /*
         * Base JSON object:
         *
         * {
         *    "thingId": "0x1337",
         *    "foo": {
         *       "bar": {
         *          "baz": 23,
         *          "oogle": "boogle"
         *       },
         *       "yo": 10
         *    },
         *    "isOn": false
         * }
         */

        final Map<String, JsonField> barValues = toMap(KNOWN_KEY_BAZ, KNOWN_INT_23);
        barValues.put("oogle", toField("oogle", JsonValue.of("boogle")));
        final ImmutableJsonObject barJsonObject = ImmutableJsonObject.of(barValues);

        final Map<String, JsonField> fooValues = toMap(KNOWN_KEY_BAR, barJsonObject);
        final int intValue10 = 10;
        fooValues.put("yo", toField("yo", JsonValue.of(intValue10)));
        final JsonObject fooJsonObject = ImmutableJsonObject.of(fooValues);

        final Map<String, JsonField> values = toMap("thingId", JsonValue.of("0x1337"));
        values.put(KNOWN_KEY_FOO.toString(), toField(KNOWN_KEY_FOO, fooJsonObject));
        values.put("isOn", toField("isOn", JsonValue.of(false)));

        final JsonObject underTest = ImmutableJsonObject.of(values);

        final CharSequence jsonPointer = JsonFactory.newPointer(KNOWN_KEY_FOO, KNOWN_KEY_BAR, KNOWN_KEY_BAZ);

        assertThat(underTest.contains(jsonPointer)).isTrue();
    }

    @Test
    public void containsShouldReturnFalseOnPointerDeeperThanObject() {
        // When JsonObject
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, KNOWN_VALUE_BAR));
        // pointer that goes deeper
        final JsonPointer deeperThanObject = KNOWN_KEY_FOO.asPointer().append(JsonPointer.of("/foo/not/known/path"));

        assertThat(underTest.contains(deeperThanObject)).isFalse();
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetJsonObjectWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.get((JsonPointer) null);
    }

    @Test
    public void tryToGetJsonObjectWithEmptyJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, JsonValue.of(KNOWN_INT_42)));

        final JsonObject resultForEmptyPointer = underTest.get(JsonFactory.emptyPointer());

        assertThat(resultForEmptyPointer).isSameAs(underTest);
    }

    @Test
    public void getWithJsonPointerReturnsExpected() {
        final ImmutableJsonObject someAttr = ImmutableJsonObject.of(toMap("subsel", JsonValue.of
                (KNOWN_INT_42)));

        final JsonObject expected = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("someAttr", someAttr)
                        .build())
                .build();

        final JsonObject underTest = ImmutableJsonObject.of(toMap("thingId", JsonValue.of("myThing")))
                .setValue("attributes", JsonObject.newBuilder()
                        .set("someAttr", someAttr)
                        .set("anotherAttr", KNOWN_VALUE_BAR)
                        .build());

        final JsonPointer jsonPointer = JsonPointer.of("attributes/someAttr/subsel");
        final JsonObject actual = underTest.get(jsonPointer);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void getEmptyJsonObjectIfJsonPointerPointsAtNonExistingValue() {
        final JsonPointer jsonPointer = JsonPointer.of("/nested/bla/blub");
        final JsonObject expected = ImmutableJsonObject.empty();

        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS)
                .setValue("nested", ImmutableJsonObject.empty()
                        .setValue("subnested", ImmutableJsonObject.empty()
                                .setValue("key", "value")));

        final JsonObject actual = underTest.get(jsonPointer);

        assertThat(actual).isEqualTo(expected);
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetValueForNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.getValue((JsonPointer) null);
    }

    @Test
    public void getValueForEmptyJsonPointerReturnsEmptyOptional() {
        final JsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, KNOWN_INT_23));

        final Optional<JsonValue> value = underTest.getValue(JsonFactory.emptyPointer());

        assertThat(value).contains(underTest);
    }

    @Test
    public void getExistingValueForPointerReturnsExpected() {
        /*
         * JSON object:
         *
         * {
         *    "someObjectAttribute": {
         *       "someKey": {
         *          "someNestedKey": 42
         *       }
         *    }
         * }
         */
        final JsonObject nestedJsonObject = ImmutableJsonObject.of(toMap("someNestedKey", KNOWN_INT_42));
        final JsonObject attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", nestedJsonObject));
        final JsonObject underTest = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));
        final JsonPointer jsonPointer = JsonPointer.of("someObjectAttribute/someKey/someNestedKey");

        final JsonValue expected = JsonValue.of(KNOWN_INT_42);
        final Optional<JsonValue> actual = underTest.getValue(jsonPointer);

        assertThat(actual).contains(expected);
    }

    @Test
    public void getFieldWithEmptyStringReturnsEmptyOptional() {
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));

        assertThat(underTest.getField("")).isEmpty();
    }

    @Test
    public void getFieldWithSlashStringReturnsEmptyOptional() {
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));

        assertThat(underTest.getField("/")).isEmpty();
    }

    @Test
    public void getFieldWithSlashJsonKeyReturnsExpected() {
        final JsonKey jsonKey = JsonKey.of("/");
        final JsonValue jsonValue = KNOWN_VALUE_FOO;
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(jsonKey, jsonValue));

        assertThat(underTest.getField(jsonKey)).contains(toField(jsonKey, jsonValue));
    }

    @Test
    public void getNestedFieldWithJsonPointerReturnsExpected() {
        final JsonKey jsonKey = KNOWN_KEY_BAR;
        final JsonValue jsonValue = KNOWN_VALUE_BAR;
        final ImmutableJsonObject barJsonObject = ImmutableJsonObject.of(toMap(jsonKey, jsonValue));
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(toMap(KNOWN_KEY_FOO, barJsonObject));

        assertThat(underTest.getField("/foo/bar")).contains(toField(jsonKey, jsonValue));
    }

    @Test(expected = NullPointerException.class)
    public void tryToGetJsonObjectWithNullJsonFieldSelector() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.get((JsonFieldSelector) null);
    }

    @Test
    public void getWithEmptyJsonFieldSelectorReturnsEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final ImmutableJsonFieldSelector jsonFieldSelector = ImmutableJsonFieldSelector.of(Collections.emptySet());
        final JsonObject actual = underTest.get(jsonFieldSelector);

        assertThat(actual).isEmpty();
    }

    @Test
    public void getWithJsonFieldSelectorReturnsExpected() {
        /*
         * Base JSON object:
         *
         * {
         *    "thingId": "0x1337",
         *    "foo": {
         *       "bar": {
         *          "baz": 23,
         *          "oogle": "boogle"
         *       },
         *       "yo": 10
         *    },
         *    "isOn": false
         * }
         */
        JsonObject barJsonObject =
                JsonObject.newBuilder().set("baz", KNOWN_INT_23).set("oogle", "boogle").build();
        final int intValue10 = 10;
        JsonObject fooJsonObject =
                JsonObject.newBuilder().set("bar", barJsonObject).set("yo", intValue10).build();
        final JsonObject underTest =
                JsonObject.newBuilder()
                        .set("thingId", "0x1337")
                        .set("foo", fooJsonObject)
                        .set("isOn", false)
                        .build();

        final JsonPointer jsonPointer1 = JsonPointer.of("foo/bar/baz");
        final JsonPointer jsonPointer2 = JsonPointer.of("foo/yo");
        final JsonPointer jsonPointer3 = JsonPointer.of("thingId");
        final JsonFieldSelector jsonFieldSelector =
                JsonFieldSelector.newInstance(jsonPointer1, jsonPointer2, jsonPointer3);

        final JsonObject actual = underTest.get(jsonFieldSelector);

        /*
         * Expected JSON object:
         *
         * {
         *    "foo": {
         *       "bar": {
         *          "baz": 23
         *       },
         *       "yo": 10
         *    },
         *    "thingId": "0x1337",
         * }
         */
        barJsonObject = ImmutableJsonObject.of(toMap("baz", KNOWN_INT_23));
        fooJsonObject = JsonObject.newBuilder().set("bar", barJsonObject).set("yo", intValue10).build();
        final JsonObject expected =
                JsonObject.newBuilder().set("foo", fooJsonObject).set("thingId", "0x1337").build();

        // all fields should be equal.
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void jsonFieldSelectorPutsFieldsInIdenticalOrder() {
        final JsonObject underTest = JsonFactory.newObject(
                "{\"the\":1,\"quick\":2,\"brown\":3,\"fox\":{\"jumps\":5,\"over\":{\"the\":6,\"lazy\":7}},\"dog\":8}");

        assertThat(underTest.get(selector("the,quick,dog")).toString())
                .isEqualTo("{\"the\":1,\"quick\":2,\"dog\":8}");

        assertThat(underTest.get(selector("dog,quick,the")).toString())
                .isEqualTo("{\"dog\":8,\"quick\":2,\"the\":1}");

        assertThat(underTest.get(selector("dog,fox/over/lazy,quick,fox/jumps,brown,fox/over/the,the")).toString())
                .isEqualTo("{\"dog\":8,\"fox\":{\"over\":{\"lazy\":7,\"the\":6},\"jumps\":5}," +
                        "\"quick\":2,\"brown\":3,\"the\":1}");

        assertThat(underTest.get(selector("the,fox/jumps,brown,fox/over/the,quick,fox/over/lazy,dog")).toString())
                .isEqualTo("{\"the\":1,\"fox\":{\"jumps\":5,\"over\":{\"the\":6,\"lazy\":7}}," +
                        "\"brown\":3,\"quick\":2,\"dog\":8}");

        assertThat(underTest.get(selector("fox/(jumps,over/(the,lazy))")).toString())
                .isEqualTo("{\"fox\":{\"jumps\":5,\"over\":{\"the\":6,\"lazy\":7}}}");

        assertThat(underTest.get(selector("fox/(over/(lazy,the),jumps)")).toString())
                .isEqualTo("{\"fox\":{\"over\":{\"lazy\":7,\"the\":6},\"jumps\":5}}");
    }

    @Test
    public void getWithEmptyFieldSelectorReturnsEmptyObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(underTest.get(fieldSelector)).isEmpty();
    }

    @Test
    public void getWithFieldSelectorOnEmptyObjectReturnsEmptyObject() {
        final JsonObject empty = ImmutableJsonObject.empty();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("root/child/sub",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(empty.get(fieldSelector)).isSameAs(empty);
    }

    @Test
    public void toStringReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.toString()).isEqualTo(KNOWN_JSON_STRING);
    }

    @Test
    public void createJsonObjectFromStringWhereFieldValueIsStringNull() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        stringBuilder.append("\"event\": \"" + "unlikelyEvent" + "\"");
        stringBuilder.append(",\"payload\": {");
        stringBuilder.append("\"thingId\": \"").append("thingId").append("\"");
        stringBuilder.append("}}");
        final String jsonString = stringBuilder.toString();

        final JsonObject jsonObject = JsonObject.of(jsonString);
        final Optional<JsonValue> owner = jsonObject.getValue("owner");

        assertThat(owner).isEmpty();
    }

    @Test
    public void setFieldAppendsFieldIfNoPreviousFieldWithSameKeyExists() {
        final JsonField newField = toField("myKey", JsonValue.of("myValue"));
        JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest = underTest.set(newField);

        assertThat(underTest)
                .hasSize(KNOWN_FIELDS.size() + 1)
                .contains(newField);
    }

    @Test
    public void setFieldReplacesExistingFieldWithSameKey() {
        final JsonField changedField = toField(KNOWN_KEY_BAR, JsonValue.of("myValue"));
        JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest = underTest.set(changedField);

        assertThat(underTest)
                .hasSize(KNOWN_FIELDS.size())
                .contains(changedField);
    }

    @Test
    public void setValueViaFieldDefinitionOnEmptyObject() {
        final JsonPointer pointer = JsonPointer.of("foo/bar/baz");
        final JsonFieldDefinition<Integer> fieldDefinition = JsonFactory.newIntFieldDefinition(pointer);
        final JsonValue value = JsonValue.of(KNOWN_INT_23);

        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject withValue = underTest.set(fieldDefinition, value.asInt());

        assertThat(withValue).contains(fieldDefinition, value);
    }

    @Test
    public void setValueViaFieldDefinitionOnNonEmptyObject() {
        // ARRANGE
        final JsonPointer pointer = JsonPointer.of("foo/bar/baz");
        final JsonFieldDefinition<Integer> fieldDefinition = JsonFactory.newIntFieldDefinition(pointer);
        final JsonValue value = JsonValue.of(KNOWN_INT_42);

        final JsonObject bar = ImmutableJsonObject.empty()
                .setValue(KNOWN_KEY_BAZ, ImmutableJsonObject.empty())
                .setValue("oogle", KNOWN_INT_23);
        final JsonObject foo = ImmutableJsonObject.empty().setValue(KNOWN_KEY_BAR, bar);

        final Map<String, JsonField> fieldsFoo = toMap(KNOWN_KEY_FOO, foo);

        final JsonObject expectedJsonObject = ImmutableJsonObject.empty()
                .setValue(KNOWN_KEY_FOO, ImmutableJsonObject.empty()
                        .setValue(KNOWN_KEY_BAR, ImmutableJsonObject.empty()
                                .set(JsonField.newInstance(KNOWN_KEY_BAZ, value, fieldDefinition))
                                .setValue("oogle", KNOWN_INT_23)));

        // ACT
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(fieldsFoo);
        final JsonObject withValue = underTest.set(fieldDefinition, value.asInt());

        // ASSERT
        assertThat(withValue).isEqualTo(expectedJsonObject);
    }

    @Test
    public void shouldHandleOverlappingFieldSelectors() {
        final Map<String, JsonField> jsonFieldMap = toMap("x", JsonObject.of("{\"y\":1,\"z\":2}"));
        jsonFieldMap.put("w", toField("w", JsonValue.of(3)));
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(jsonFieldMap);

        // a field selector is overlapping if one pointer is a prefix of another.
        final JsonFieldSelector overlappingSelector = JsonFieldSelector.newInstance("x/y", "x/z", "x");
        final JsonObject actual = underTest.get(overlappingSelector);

        assertThat(actual).isEqualTo(underTest.remove("w"));
    }

    @Test
    public void emptyAndNonexistentPointersHaveNoEffectInFieldSelector() {
        final String jsonString = "{\"x\":{\"y\":1,\"z\":2},\"w\":3}";
        final JsonObject underTest = JsonObject.of(jsonString);
        final JsonFieldSelector overlappingSelectorWithRoot =
                JsonFieldSelector.newInstance("w", "a/b/c", "/");
        final JsonObject actual = underTest.get(overlappingSelectorWithRoot);
        assertThat(actual).isEqualTo(underTest.remove("x"));
    }

    @Test
    public void partiallyIntersectingPointersHaveNoEffectInFieldSelector() {
        final String jsonString = "{\"x\":{\"y\":1,\"z\":2},\"w\":3}";
        final JsonObject underTest = JsonObject.of(jsonString);

        // a pointer intersects partially if the json object contains a prefix of it but does not contain itself.
        final JsonFieldSelector partiallyIntersecting =
                JsonFieldSelector.newInstance("x/a/b/c", "w");

        final JsonObject actual = underTest.get(partiallyIntersecting);
        assertThat(actual).isEqualTo(underTest.remove("x"));
    }

    @Test
    public void jsonObjectsNestedInArraysShouldCompareWithoutFieldDefinitions() {
        final JsonObject objectWithoutDefinition =
                ImmutableJsonObject.of(toMap("x", JsonArray.of(JsonObject.of("{\"y\":5}"))));
        final JsonObject objectWithDefinition = JsonObject.newBuilder()
                .set(JsonFactory.newJsonArrayFieldDefinition("x"), JsonArray.newBuilder()
                        .add(JsonObject.newBuilder()
                                .set(JsonFactory.newIntFieldDefinition("y"), 5)
                                .build())
                        .build())
                .build();

        assertThat(objectWithoutDefinition).isEqualToIgnoringFieldDefinitions(objectWithDefinition);
        Assertions.assertThat(objectWithoutDefinition).isEqualTo(objectWithDefinition);
    }

    private static Map<String, JsonField> toMap(final CharSequence key, final JsonValue value) {
        final Map<String, JsonField> result = new LinkedHashMap<>();
        result.put(key.toString(), toField(key, value));
        return result;
    }

    private static JsonField toField(final CharSequence key, final JsonValue value) {
        return JsonField.newInstance(JsonKey.of(key), value);
    }

    private static JsonFieldSelector selector(final String s) {
        return JsonFactory.newFieldSelector(s, JsonParseOptions.newBuilder().withoutUrlDecoding().build());
    }

    @Test
    public void validateSoftReferenceStrategy() throws IllegalAccessException, NoSuchFieldException {
        final ImmutableJsonObject jsonObject = ImmutableJsonObject.of(KNOWN_FIELDS);
        assertInternalCachesAreAsExpected(jsonObject, true);

        final Field valueListField = jsonObject.getClass().getDeclaredField("fieldMap");
        valueListField.setAccessible(true);
        final ImmutableJsonObject.SoftReferencedFieldMap
                valueList = (ImmutableJsonObject.SoftReferencedFieldMap) valueListField.get(jsonObject);

        final Field softReferenceField = valueList.getClass().getDeclaredField("fieldsReference");
        softReferenceField.setAccessible(true);
        SoftReference softReference = (SoftReference) softReferenceField.get(valueList);

        softReference.clear();

        assertThat(jsonObject.getValue(KNOWN_KEY_FOO)).isPresent();
    }

    private void assertInternalCachesAreAsExpected(final JsonObject jsonObject, final boolean jsonExpected) {
        try {
            final Field valueListField = jsonObject.getClass().getDeclaredField("fieldMap");
            valueListField.setAccessible(true);
            final ImmutableJsonObject.SoftReferencedFieldMap
                    fieldMap = (ImmutableJsonObject.SoftReferencedFieldMap) valueListField.get(jsonObject);

            final Field jsonStringField = fieldMap.getClass().getDeclaredField("jsonObjectStringRepresentation");
            jsonStringField.setAccessible(true);
            String jsonString = (String) jsonStringField.get(fieldMap);

            assertThat(jsonString != null).isEqualTo(jsonExpected);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            System.err.println(
                    "Failed to access internal caching fields in JsonObject using reflection. " +
                            "This might just be a bug in the test."
            );
            e.printStackTrace();
        }
    }

}
