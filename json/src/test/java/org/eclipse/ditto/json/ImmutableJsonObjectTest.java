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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonObject}.
 */
@SuppressWarnings("ConstantConditions")
public final class ImmutableJsonObjectTest {

    private static final int KNOWN_INT_23 = 23;
    private static final int KNOWN_INT_42 = 42;
    private static final JsonKey KNOWN_KEY_FOO = JsonFactory.newKey("foo");
    private static final JsonKey KNOWN_KEY_BAR = JsonFactory.newKey("bar");
    private static final JsonKey KNOWN_KEY_BAZ = JsonFactory.newKey("baz");
    private static final JsonValue KNOWN_VALUE_FOO = JsonFactory.newValue("bar");
    private static final JsonValue KNOWN_VALUE_BAR = JsonFactory.newValue("baz");
    private static final JsonValue KNOWN_VALUE_BAZ = JsonFactory.newValue(KNOWN_INT_42);
    private static final Map<String, JsonField> KNOWN_FIELDS = new LinkedHashMap<>();
    private static final String KNOWN_JSON_STRING;

    static {
        KNOWN_FIELDS.put(KNOWN_KEY_FOO.toString(), JsonFactory.newField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        KNOWN_FIELDS.put(KNOWN_KEY_BAR.toString(), JsonFactory.newField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        KNOWN_FIELDS.put(KNOWN_KEY_BAZ.toString(), JsonFactory.newField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        KNOWN_JSON_STRING = "{"
                + "\"" + KNOWN_KEY_FOO + "\":\"" + KNOWN_VALUE_FOO.asString() + "\","
                + "\"" + KNOWN_KEY_BAR + "\":\"" + KNOWN_VALUE_BAR.asString() + "\","
                + "\"" + KNOWN_KEY_BAZ + "\":" + KNOWN_VALUE_BAZ.asInt()
                + "}";
    }

    private static Map<String, JsonField> toMap(final CharSequence keyName, final int rawValue) {
        return toMap(keyName, JsonFactory.newValue(rawValue));
    }

    private static Map<String, JsonField> toMap(final CharSequence keyName, final JsonValue value) {
        return Collections.singletonMap(keyName.toString(), JsonFactory.newField(JsonFactory.newKey(keyName), value));
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonObject.class,
                areImmutable(),
                assumingFields("fields").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableJsonObject.class) //
                .withIgnoredFields("stringRepresentation") //
                .withPrefabValues(SoftReference.class, red, black) //
                .verify();
    }

    /** */
    @Test
    public void orderOfFieldsDoesNotAffectEquality() {
        final JsonObjectBuilder barJsonObjectBuilder = JsonFactory.newObjectBuilder();
        barJsonObjectBuilder.set("baz", KNOWN_INT_23);

        final JsonObjectBuilder fooJsonObjectLeftBuilder = JsonFactory.newObjectBuilder();
        fooJsonObjectLeftBuilder.set("bar", barJsonObjectBuilder.build());
        final int intValue10 = 10;
        fooJsonObjectLeftBuilder.set("yo", intValue10);

        final JsonObjectBuilder leftObjectBuilder = JsonFactory.newObjectBuilder();
        leftObjectBuilder.set("foo", fooJsonObjectLeftBuilder.build());

        final JsonObjectBuilder fooJsonObjectRightBuilder = JsonFactory.newObjectBuilder();
        fooJsonObjectRightBuilder.set("yo", intValue10);
        fooJsonObjectRightBuilder.set("bar", barJsonObjectBuilder.build());

        final JsonObjectBuilder rightObjectBuilder = JsonFactory.newObjectBuilder();
        rightObjectBuilder.set("foo", fooJsonObjectRightBuilder.build());

        final JsonObject leftJsonObject = leftObjectBuilder.build();
        final JsonObject rightJsonObject = rightObjectBuilder.build();

        assertThat(leftJsonObject).isEqualTo(rightJsonObject);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetInstanceFromNullJsonObject() {
        ImmutableJsonObject.of((Map<String, JsonField>) null);
    }

    /** */
    @Test
    public void getEmptyInstanceReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isObject();
        assertThat(underTest).isEmpty();
        assertThat(underTest).hasSize(0);
        assertThat(underTest.asObject()).isSameAs(underTest);
        assertThat(underTest.toString()).isEqualTo("{}");
    }

    /** */
    @Test
    public void objectIsNothingElse() {
        final JsonValue underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isObject();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotNumber();
        assertThat(underTest).isNotString();
        assertThat(underTest).isNotArray();
    }

    /** */
    @Test
    public void checkUnsupportedOperations() {
        final JsonValue underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).doesNotSupport(JsonValue::asArray);
        assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        assertThat(underTest).doesNotSupport(JsonValue::asString);
        assertThat(underTest).doesNotSupport(JsonValue::asInt);
        assertThat(underTest).doesNotSupport(JsonValue::asLong);
        assertThat(underTest).doesNotSupport(JsonValue::asDouble);
    }

    /** */
    @Test
    public void getInstanceReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).isObject();
        assertThat(underTest).isNotEmpty();
        assertThat(underTest).hasSize(3);
        assertThat(underTest.asObject()).isSameAs(underTest);
        assertThat(underTest.toString()).isEqualTo(KNOWN_JSON_STRING);
    }

    /** */
    @Test
    public void getReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonKey key = JsonFactory.newKey("foo");
        final JsonValue expectedValue = JsonFactory.newValue("bar");

        assertThat(underTest).contains(key, expectedValue);
    }

    /** */
    @Test
    public void setWorksAsExpected() {
        final JsonKey key = JsonFactory.newKey("key");
        final JsonValue valueToAdd = JsonFactory.newValue("oxi");

        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThat(underTest).isEmpty();

        final JsonObject newJsonObject = underTest.setValue(key, valueToAdd);

        assertThat(underTest).isEmpty();
        assertThat(newJsonObject).isNotEmpty();
        assertThat(newJsonObject).hasSize(1);
        assertThat(newJsonObject).isNotSameAs(underTest);
        assertThat(newJsonObject).contains(key, valueToAdd);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToSetIntValueWithNullKey() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setValue((String) null, KNOWN_INT_23);
    }

    /** */
    @Test
    public void tryToSetIntValueWithEmptyKey() {
        final JsonObject underTest = ImmutableJsonObject.empty();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.setValue("", KNOWN_INT_23))
                .withMessage("The key of the value to set, get or remove must not be empty!")
                .withNoCause();
    }

    /** */
    @Test
    public void setIntCreatesDisjointJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonFactory.newKey("key");
        final int value = KNOWN_INT_42;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(1);
        assertThat(afterAdd).contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    /** */
    @Test
    public void setIntWithKeyContainingSlashes() {
        final CharSequence key = "foo/bar/baz";
        final JsonKey jsonKey = JsonFactory.newKey(key);
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        final JsonObject jsonObjectWithIntValue = underTest.setValue(key, KNOWN_INT_42);

        assertThat(jsonObjectWithIntValue).contains(jsonKey, KNOWN_INT_42);
    }

    /** */
    @Test
    public void setFieldTwiceReturnsSameInstance() {
        final JsonKey jsonKey = JsonFactory.newKey("foo/bar/baz");
        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();

        final JsonObject jsonObjectWithIntValue1 = underTest.setValue(jsonKey, KNOWN_INT_42);
        final JsonObject jsonObjectWithIntValue2 = jsonObjectWithIntValue1.setValue(jsonKey, KNOWN_INT_42);

        assertThat(jsonObjectWithIntValue2)
                .contains(jsonKey, KNOWN_INT_42)
                .isSameAs(jsonObjectWithIntValue1);
    }

    /** */
    @Test
    public void setLongCreatesDisjunctJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonFactory.newKey("key");
        final long value = Long.MAX_VALUE;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(1);
        assertThat(afterAdd).contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    /** */
    @Test
    public void setDoubleCreatesDisjunctJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonFactory.newKey("key");
        final double value = Double.MAX_VALUE;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(1);
        assertThat(afterAdd).contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    /** */
    @Test
    public void setBooleanCreatesDisjunctJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonFactory.newKey("key");
        final boolean value = false;
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(1);
        assertThat(afterAdd).contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    /** */
    @Test
    public void setStringCreatesDisjunctJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonKey key = JsonFactory.newKey("key");
        final String value = "black out";
        final JsonObject afterAdd = underTest.setValue(key, value);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(1);
        assertThat(afterAdd).contains(key, value);

        underTest.setValue(KNOWN_KEY_FOO, KNOWN_VALUE_FOO);

        assertThat(afterAdd).doesNotContain(KNOWN_KEY_FOO);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToSetValueWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setValue((JsonPointer) null, mock(JsonValue.class));
    }

    /** */
    @Test
    public void tryToSetValueWithEmptyJsonPointer() {
        final JsonPointer jsonPointer = newPointer("foo");
        final JsonPointer emptyJsonPointer = jsonPointer.nextLevel();

        assertThat(emptyJsonPointer).isEmpty();

        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonValue value = JsonValue.newInstance(42);
        final JsonObject returned = underTest.setValue(emptyJsonPointer, value);

        assertThat((Iterable) returned).containsOnlyOnce(JsonField.newInstance("/", value));
    }

    /** */
    @Test
    public void setValueWithJsonPointerToEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonPointer jsonPointer = newPointer("foo/bar/baz");
        final JsonValue valueToAdd = JsonFactory.newValue(KNOWN_INT_23);
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
        final JsonObjectBuilder bazJsonObjectBuilder = JsonFactory.newObjectBuilder();
        bazJsonObjectBuilder.set("baz", valueToAdd);

        final JsonObjectBuilder barJsonObjectBuilder = JsonFactory.newObjectBuilder();
        barJsonObjectBuilder.set("bar", bazJsonObjectBuilder.build());

        final JsonObjectBuilder fooJsonObjectBuilder = JsonFactory.newObjectBuilder();
        fooJsonObjectBuilder.set("foo", barJsonObjectBuilder.build());

        final JsonObject expected = fooJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
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
        final JsonObject barJsonObject = JsonFactory.newObjectBuilder().set("bar", bazJsonObject).set("yo", intValue10).build();

        final JsonObject underTest = ImmutableJsonObject.of(toMap("foo", barJsonObject));

        final JsonPointer jsonPointer = newPointer("foo/bar/allYourBase");
        final JsonValue valueToAdd = JsonFactory.newValue("are belong to us!");
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
        final JsonObjectBuilder bazJsonObjectBuilder = JsonFactory.newObjectBuilder();
        bazJsonObjectBuilder.set("baz", KNOWN_INT_23);
        bazJsonObjectBuilder.set("allYourBase", valueToAdd);

        final JsonObjectBuilder barJsonObjectBuilder = JsonFactory.newObjectBuilder();
        barJsonObjectBuilder.set("bar", bazJsonObjectBuilder.build());
        barJsonObjectBuilder.set("yo", intValue10);

        final JsonObjectBuilder fooJsonObjectBuilder = JsonFactory.newObjectBuilder();
        fooJsonObjectBuilder.set("foo", barJsonObjectBuilder.build());

        final JsonObject expected = fooJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToAddAllWithNullJsonFieldsIterable() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.setAll(null);
    }

    /** */
    @Test
    public void setAllWithEmptyJsonFieldIterableReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject sameAsBefore = underTest.setAll(Collections.emptyList());

        assertThat(underTest).isSameAs(sameAsBefore);
    }

    /** */
    @Test
    public void setAllJsonFieldsWorksAsExpected() {
        final Collection<JsonField> jsonFieldsToAdd = new ArrayList<>();
        jsonFieldsToAdd.add(JsonFactory.newField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        jsonFieldsToAdd.add(JsonFactory.newField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        jsonFieldsToAdd.add(JsonFactory.newField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        final JsonObject emptyJsonObject = ImmutableJsonObject.empty();
        final JsonObject underTest = emptyJsonObject.setAll(jsonFieldsToAdd);

        assertThat(underTest)
                .hasSize(jsonFieldsToAdd.size())
                .containsExactlyElementsOf(jsonFieldsToAdd);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetValueByNullName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.getValue((String) null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void tryToGetValueByEmptyName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.getValue("");
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetValueByNullKey() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.getValue((JsonKey) null);
    }

    /** */
    @Test
    public void getExistingValueByName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.getValue(KNOWN_KEY_BAZ)).contains(KNOWN_VALUE_BAZ);
    }

    /** */
    @Test
    public void getNonExistingValueByNameReturnsEmptyOptional() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.getValue("waldo")).isEmpty();
    }

    /** */
    @Test
    public void getExistingValueByKeyReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest).contains(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToRemoveJsonFieldByNullName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.remove((String) null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void tryToRemoveJsonFieldByEmptyName() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.remove("");
    }

    /** */
    @Test
    public void removeNonExistingJsonFieldByNameReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove("schroedinger");

        assertThat(afterRemoval).isSameAs(underTest);
    }

    /** */
    @Test
    public void removeNonExistingJsonFieldByKeyReturnsSameJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove(JsonFactory.newKey("schroedinger"));

        assertThat(afterRemoval).isSameAs(underTest);
    }

    /** */
    @Test
    public void removingExistingJsonFieldByNameReturnsDisjunctJsonObject() {
        final String nameToRemove = KNOWN_KEY_BAR.toString();
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonObject afterRemoval = underTest.remove(nameToRemove);
        final int expectedSize = underTest.getSize() - 1;

        assertThat(afterRemoval).isNotSameAs(underTest);
        assertThat(afterRemoval).hasSize(expectedSize);
        assertThat(afterRemoval).doesNotContain(KNOWN_KEY_BAR);

        underTest.remove(KNOWN_KEY_BAZ);

        assertThat(afterRemoval)
                .contains(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ)
                .as("Another removal on original"
                + " JSON object has no influence on the JSON object which was created after first removal.");
    }

    /** */
    @Test
    public void removingSubAttributesWorksAsExpected() {
        final Map<String, JsonField> jsonFields = new LinkedHashMap<>();
        jsonFields.put(KNOWN_KEY_FOO.toString(), JsonFactory.newField(KNOWN_KEY_FOO, JsonFactory.newValue(123)));
        jsonFields.put(KNOWN_KEY_BAR.toString(), JsonFactory.newField(KNOWN_KEY_BAR, JsonFactory.newValue(true)));
        jsonFields.put(KNOWN_KEY_BAZ.toString(),
                JsonFactory.newField(KNOWN_KEY_BAZ, JsonFactory.newObject("{\"bla\":\"blub\"}")));
        final JsonField complex =
                JsonFactory.newField(JsonFactory.newKey("complex"), JsonFactory.newObject("{\"subToDelete\":42}"));
        jsonFields.put(complex.getKeyName(), complex);

        final JsonObject underTest = ImmutableJsonObject.of(jsonFields);

        final JsonPointer jsonPointer = JsonFactory.newPointer("complex/subToDelete");
        final JsonObject afterRemoval = underTest.remove(jsonPointer);
        jsonFields.remove(complex.getKeyName());
        jsonFields.put(complex.getKeyName(), JsonFactory.newField(complex.getKey(), JsonFactory.newObject("{}")));

        assertThat(afterRemoval).isNotSameAs(underTest);
        assertThat(afterRemoval).doesNotContain(JsonFactory.newKey("subToDelete"));
        assertThat(afterRemoval).containsExactlyElementsOf(jsonFields.values());
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToRemoveJsonFieldByNullKey() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest.remove((JsonKey) null);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToRemoveWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.remove((JsonPointer) null);
    }

    /** */
    @Test
    public void tryToRemoveWithEmptyJsonPointer() {
        final JsonPointer jsonPointer = newPointer("foo");
        final JsonPointer emptyJsonPointer = jsonPointer.nextLevel();

        assertThat((Iterable) emptyJsonPointer).isEmpty();

        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject returned = underTest.remove(emptyJsonPointer);
        assertThat(returned).isEqualTo(underTest);
    }

    /** */
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

        final JsonPointer jsonPointer = newPointer("someObjectAttribute/someKey/someNestedKey");

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
        attributeJsonObject = ImmutableJsonObject.of(toMap("someKey", JsonFactory.newObject()));

        final JsonObject expected = ImmutableJsonObject.of(toMap("someObjectAttribute", attributeJsonObject));

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void getKeysReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonKey[] expectedKeys = new JsonKey[]{KNOWN_KEY_FOO, KNOWN_KEY_BAR, KNOWN_KEY_BAZ};
        final List<JsonKey> actualKeys = underTest.getKeys();

        assertThat(actualKeys).containsOnly(expectedKeys);
    }

    /** */
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
        final JsonPointer jsonPointer = newPointer("someObjectAttribute/someKey/someNestedKey");

        final JsonValue valueToSet = JsonFactory.newValue("monday");
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

    /** */
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

        final JsonValue jsonValue = JsonFactory.readFrom("{\"thingId\":\"998bed03-2350-473a-8c42-b9c5558cf8af\","
                + "\"attributes\":{\"manufacturer\":\"ACME\",\"make\":\"Fancy Fab Car\","
                + "\"model\":\"Environmental FourWheeler 4711\",\"VIN\":\"0815666337\"},"
                + "\"features\":{\"Vehicle\":{\"featureId\":\"Vehicle\",\"functionblock\":null,"
                + "\"properties\":{\"configuration\":{\"transmission\":{\"type\":\"manual\",\"gears\":7}},"
                + "\"status\":{\"running\":true,\"speed\":90,\"gear\":5},\"fault\":{\"flatTyre\":false}}},"
                + "\"EnvironmentScanner\":{\"featureId\":\"EnvironmentScanner\",\"functionblock\":null,"
                + "\"properties\":{\"temperature\":20.8,\"humidity\":73,\"barometricPressure\":970.7,"
                + "\"location\":{\"longitude\":47.682170,\"latitude\":9.386372},\"altitude\":399}}}}");
        JsonObject underTest = jsonValue.asObject();
        underTest = underTest.setValue(newPointer("/foo/bar/baz"), JsonFactory.newObject("{\"alice\":\"bob\"}"));

        final JsonObjectBuilder bazObjectBuilder = JsonFactory.newObjectBuilder();
        bazObjectBuilder.set("alice", "bob");

        final JsonObjectBuilder barObjectBuilder = JsonFactory.newObjectBuilder();
        barObjectBuilder.set("baz", bazObjectBuilder.build());

        final JsonObjectBuilder fooObjectBuilder = JsonFactory.newObjectBuilder();
        fooObjectBuilder.set("bar", barObjectBuilder.build());
        final JsonObject expectedFoo = fooObjectBuilder.build();

        final Optional<JsonValue> jsonValueOptional = underTest.getValue("foo");

        assertThat(jsonValueOptional).contains(expectedFoo);
    }

    /** */
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
        final JsonPointer jsonPointer = newPointer("someObjectAttribute/isGroovy");

        final JsonValue valueToSet = JsonFactory.newValue(false);
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

        final JsonObjectBuilder attributeJsonObjectBuilder = JsonFactory.newObjectBuilder();
        attributeJsonObjectBuilder.set("someKey", nestedJsonObject);
        attributeJsonObjectBuilder.set("isGroovy", valueToSet);

        final JsonObjectBuilder rootJsonObjectBuilder = JsonFactory.newObjectBuilder();
        rootJsonObjectBuilder.set("someObjectAttribute", attributeJsonObjectBuilder.build());

        final JsonObject expected = rootJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void setValueThroughPointerOnEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonPointer jsonPointer = newPointer("someObjectAttribute/someKey/someNestedKey");
        final JsonValue valueToSet = JsonFactory.newValue("monday");
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
        final JsonObjectBuilder nestedJsonObjectBuilder = JsonFactory.newObjectBuilder();
        nestedJsonObjectBuilder.set("someNestedKey", valueToSet);

        final JsonObjectBuilder attributeJsonObjectBuilder = JsonFactory.newObjectBuilder();
        attributeJsonObjectBuilder.set("someKey", nestedJsonObjectBuilder.build());

        final JsonObjectBuilder rootJsonObjectBuilder = JsonFactory.newObjectBuilder();
        rootJsonObjectBuilder.set("someObjectAttribute", attributeJsonObjectBuilder.build());

        final JsonObject expected = rootJsonObjectBuilder.build();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void setValueThroughPointerOnNonEmptyJsonObject() {
        final JsonValue jsonValue = JsonFactory.readFrom("{\"thingId\":\"the_thing\"}");
        final JsonObject underTest = jsonValue.asObject();

        final JsonValue additionalJsonValue = JsonFactory.readFrom("{\"foo\":\"bar\"}");
        final JsonPointer pointer = newPointer("attributes/the_attribute");

        final JsonObject actual = underTest.setValue(pointer, additionalJsonValue);
        final JsonObject expected = JsonFactory
                .readFrom("{\"thingId\":\"the_thing\"," + "\"attributes\":{\"the_attribute\":{\"foo\":\"bar\"}}}")
                .asObject();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void iteratorWorksAsExpected() {
        final JsonObject jsonObject = ImmutableJsonObject.of(KNOWN_FIELDS);
        final List<JsonField> expectedJsonFields = new ArrayList<>();
        expectedJsonFields.add(JsonFactory.newField(KNOWN_KEY_FOO, KNOWN_VALUE_FOO));
        expectedJsonFields.add(JsonFactory.newField(KNOWN_KEY_BAR, KNOWN_VALUE_BAR));
        expectedJsonFields.add(JsonFactory.newField(KNOWN_KEY_BAZ, KNOWN_VALUE_BAZ));

        final Iterator<JsonField> underTest = jsonObject.iterator();
        int index = 0;
        while (underTest.hasNext()) {
            assertThat(index).isEqualTo(expectedJsonFields.indexOf(underTest.next()));
            index++;
        }
        final int actualSize = index;

        assertThat(actualSize).isEqualTo(expectedJsonFields.size());
    }

    /** */
    @Test(expected = UnsupportedOperationException.class)
    public void iteratorDoesNotAllowChangingTheJsonObject() {
        final JsonObject jsonObject = ImmutableJsonObject.of(KNOWN_FIELDS);

        final Iterator<JsonField> underTest = jsonObject.iterator();

        while (underTest.hasNext()) {
            underTest.next();
            underTest.remove();
        }
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCallContainsWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.contains((JsonPointer) null);
    }

    /** */
    @Test
    public void tryToCallContainsWithEmptyJsonPointer() {
        final JsonPointer jsonPointer = newPointer("foo");
        final JsonPointer emptyJsonPointer = jsonPointer.nextLevel();

        assertThat((Iterable) emptyJsonPointer).isEmpty();

        final JsonObject underTest = ImmutableJsonObject.empty();
        assertThat(underTest.contains(emptyJsonPointer)).isFalse();

        final JsonObject underTest2 = JsonFactory.newObjectBuilder().set("/", true).build();
        assertThat(underTest2.contains(emptyJsonPointer)).isTrue();
    }

    /** */
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

        final JsonObject barJsonObject = JsonFactory.newObjectBuilder().set("baz", KNOWN_INT_23).set("oogle", "boogle").build();
        final int intValue10 = 10;
        final JsonObject fooJsonObject = JsonFactory.newObjectBuilder().set("bar", barJsonObject).set("yo", intValue10).build();
        final JsonObject underTest =
                JsonFactory.newObjectBuilder().set("thingId", "0x1337").set("foo", fooJsonObject).set("isOn", false).build();
        final JsonPointer jsonPointer = newPointer("foo/bar/baz");

        assertThat(underTest.contains(jsonPointer)).isTrue();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetJsonObjectWithNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.get((JsonPointer) null);
    }

    /** */
    @Test
    public void tryToGetJsonObjectWithEmptyJsonPointer() {
        final JsonPointer jsonPointer = newPointer("foo");
        final JsonPointer emptyJsonPointer = jsonPointer.nextLevel();

        assertThat((Iterable) emptyJsonPointer).isEmpty();

        final JsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject returned = underTest.get(emptyJsonPointer);
        assertThat(returned).isEqualTo(underTest);

        final JsonObject underTest2 = JsonFactory.newObjectBuilder().set("/", "foo").build();
        final JsonObject returned2 = underTest2.get(JsonPointer.empty());
        assertThat(returned2).isEqualTo(underTest2);
    }

    /** */
    @Test
    public void getJsonPointerReturnsExpected() {
        JsonObject underTest = ImmutableJsonObject.empty();
        underTest = underTest.setValue("thingId", "myThing");

        final JsonObject someAttr = JsonFactory.newObject().setValue("subsel", KNOWN_INT_42);

        final JsonObjectBuilder attributesJsonObjectBuilder = JsonFactory.newObjectBuilder();
        attributesJsonObjectBuilder.set("someAttr", someAttr);
        attributesJsonObjectBuilder.set("anotherAttr", KNOWN_VALUE_BAR);
        underTest = underTest.setValue("attributes", attributesJsonObjectBuilder.build());

        final JsonPointer jsonPointer = newPointer("attributes/someAttr/subsel");
        final JsonObject actual = underTest.get(jsonPointer);

        final JsonObjectBuilder expectedAttributesJsonObjectBuilder = JsonFactory.newObjectBuilder();
        expectedAttributesJsonObjectBuilder.set("someAttr", someAttr);

        final JsonObjectBuilder expectedJsonObjectBuilder = JsonFactory.newObjectBuilder();
        expectedJsonObjectBuilder.set("attributes", expectedAttributesJsonObjectBuilder.build());

        assertThat(actual).isEqualTo(expectedJsonObjectBuilder.build());
    }

    /** */
    @Test
    public void getEmptyJsonObjectIfJsonPointerPointsAtNonExistingValue() {
        final JsonObjectBuilder subNestedJsonObjectBuilder = JsonFactory.newObjectBuilder();
        subNestedJsonObjectBuilder.set("key", "value");

        final JsonObjectBuilder nestedJsonObjectBuilder = JsonFactory.newObjectBuilder();
        nestedJsonObjectBuilder.set("subNested", subNestedJsonObjectBuilder.build());

        JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest = underTest.setValue("nested", nestedJsonObjectBuilder.build());

        final JsonPointer jsonPointer = newPointer("nested/bla/blub");
        final JsonObject actual = underTest.get(jsonPointer);
        final JsonObject expected = ImmutableJsonObject.empty();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetValueForNullJsonPointer() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.getValue((JsonPointer) null);
    }

    /** */
    @Test
    public void tryToGetValueForEmptyJsonPointer() {
        final JsonPointer jsonPointer = newPointer("foo");
        final JsonPointer emptyJsonPointer = jsonPointer.nextLevel();

        assertThat((Iterable) emptyJsonPointer).isEmpty();

        final JsonObject underTest = ImmutableJsonObject.empty();
        final Optional<JsonValue> returned = underTest.getValue(emptyJsonPointer);
        assertThat(returned).isEmpty();

        final JsonObject underTest2 = JsonFactory.newObjectBuilder().set(JsonPointer.empty(), "bumlux").build();
        final Optional<JsonValue> returned2 = underTest2.getValue("/");
        assertThat(returned2).contains(JsonValue.newInstance("bumlux"));
    }

    /** */
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
        final JsonPointer jsonPointer = newPointer("someObjectAttribute/someKey/someNestedKey");

        final JsonValue expected = JsonFactory.newValue(KNOWN_INT_42);
        final Optional<JsonValue> actual = underTest.getValue(jsonPointer);

        assertThat(actual).contains(expected);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToGetJsonObjectWithNullJsonFieldSelector() {
        final JsonObject underTest = ImmutableJsonObject.empty();
        underTest.get((JsonFieldSelector) null);
    }

    /** */
    @Test
    public void getWithEmptyJsonFieldSelectorReturnsEmptyJsonObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final ImmutableJsonFieldSelector jsonFieldSelector = ImmutableJsonFieldSelector.of(Collections.emptySet());
        final JsonObject actual = underTest.get(jsonFieldSelector);

        assertThat(actual).isEmpty();
    }

    /** */
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
        JsonObject barJsonObject = JsonFactory.newObjectBuilder().set("baz", KNOWN_INT_23).set("oogle", "boogle").build();
        final int intValue10 = 10;
        JsonObject fooJsonObject = JsonFactory.newObjectBuilder().set("bar", barJsonObject).set("yo", intValue10).build();
        final JsonObject underTest =
                JsonFactory.newObjectBuilder().set("thingId", "0x1337").set("foo", fooJsonObject).set("isOn", false).build();

        final JsonPointer jsonPointer1 = newPointer("foo/bar/baz");
        final JsonPointer jsonPointer2 = newPointer("foo/yo");
        final JsonPointer jsonPointer3 = newPointer("thingId");
        final JsonFieldSelector jsonFieldSelector =
                JsonFactory.newFieldSelector(jsonPointer1, jsonPointer2, jsonPointer3);

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
        fooJsonObject = JsonFactory.newObjectBuilder().set("bar", barJsonObject).set("yo", intValue10).build();
        final JsonObject expected = JsonFactory.newObjectBuilder().set("foo", fooJsonObject).set("thingId", "0x1337").build();

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void getWithEmptyFieldSelectorReturnsEmptyObject() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(underTest.get(fieldSelector)).isEmpty();
    }

    /** */
    @Test
    public void getWithFieldSelectorOnEmptyObjectReturnsEmptyObject() {
        final JsonObject empty = ImmutableJsonObject.empty();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("root/child/sub",
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(empty.get(fieldSelector)).isSameAs(empty);
    }

    /** */
    @Test
    public void toStringReturnsExpected() {
        final JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);

        assertThat(underTest.toString()).isEqualTo(KNOWN_JSON_STRING);
    }

    /** */
    @Test
    public void createJsonObjectFromStringWhereFieldValueIsStringNull() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        stringBuilder.append("\"event\": \"" + "unlikelyEvent" + "\"");
        stringBuilder.append(",\"payload\": {");
        stringBuilder.append("\"thingId\": \"").append("thingId").append("\"");
        stringBuilder.append("}}");
        final String jsonString = stringBuilder.toString();

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);
        final Optional<JsonValue> owner = jsonObject.getValue("owner");

        assertThat(owner).isEmpty();
    }

    /** */
    @Test
    public void setFieldAppendsFieldIfNoPreviousFieldWithSameKeyExists() {
        final JsonField newField = JsonFactory.newField(JsonFactory.newKey("myKey"), JsonFactory.newValue("myValue"));
        JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest = underTest.set(newField);

        assertThat(underTest).hasSize(KNOWN_FIELDS.size() + 1);
        assertThat(underTest).contains(newField);
    }

    /** */
    @Test
    public void setFieldReplacesExistingFieldWithSameKey() {
        final JsonField changedField = JsonFactory.newField(KNOWN_KEY_BAR, JsonFactory.newValue("myValue"));
        JsonObject underTest = ImmutableJsonObject.of(KNOWN_FIELDS);
        underTest = underTest.set(changedField);

        assertThat(underTest).hasSize(KNOWN_FIELDS.size());
        assertThat(underTest).contains(changedField);
    }

    /** */
    @Test
    public void setValueViaFieldDefinitionOnEmptyObject() {
        final JsonPointer pointer = JsonFactory.newPointer("foo/bar/baz");
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(pointer, int.class);
        final JsonValue value = JsonFactory.newValue(KNOWN_INT_23);

        final ImmutableJsonObject underTest = ImmutableJsonObject.empty();
        final JsonObject withValue = underTest.set(fieldDefinition, value);

        assertThat(withValue).contains(fieldDefinition, value);
    }

    /** */
    @Test
    public void setValueViaFieldDefinitionOnNonEmptyObject() {
        // ARRANGE
        final JsonPointer pointer = JsonFactory.newPointer("foo/bar/baz");
        final JsonFieldDefinition fieldDefinition = JsonFactory.newFieldDefinition(pointer, int.class);
        final JsonValue value = JsonFactory.newValue(KNOWN_INT_42);

        final JsonObject bar = ImmutableJsonObject.empty()
                .setValue(KNOWN_KEY_BAZ, ImmutableJsonObject.empty())
                .setValue("oogle", KNOWN_INT_23);
        final JsonObject foo = ImmutableJsonObject.empty().setValue(KNOWN_KEY_BAR, bar);

        final Map<String, JsonField> fieldsFoo = new LinkedHashMap<>();
        fieldsFoo.put(KNOWN_KEY_FOO.toString(), JsonFactory.newField(KNOWN_KEY_FOO, foo));

        final JsonObject expectedJsonObject = ImmutableJsonObject.empty()
                .setValue(KNOWN_KEY_FOO, ImmutableJsonObject.empty()
                    .setValue(KNOWN_KEY_BAR, ImmutableJsonObject.empty()
                        .set(JsonFactory.newField(KNOWN_KEY_BAZ, value, fieldDefinition))
                        .setValue("oogle", KNOWN_INT_23)));

        // ACT
        final ImmutableJsonObject underTest = ImmutableJsonObject.of(fieldsFoo);
        final JsonObject withValue = underTest.set(fieldDefinition, value);

        // ASSERT
        assertThat(withValue).isEqualTo(expectedJsonObject);
    }

}
