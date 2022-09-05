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

import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonArray}.
 */
public final class ImmutableJsonArrayTest {

    private static final int KNOWN_INT_0 = 23;
    private static final int KNOWN_INT_1 = 42;
    private static final int KNOWN_INT_2 = 1337;
    private static final List<JsonValue> KNOWN_INT_VALUE_LIST = new ArrayList<>();

    static {
        KNOWN_INT_VALUE_LIST.add(newValue(KNOWN_INT_0));
        KNOWN_INT_VALUE_LIST.add(newValue(KNOWN_INT_1));
        KNOWN_INT_VALUE_LIST.add(newValue(KNOWN_INT_2));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonArray.class,
                areImmutable(),
                provided(ImmutableJsonArray.SoftReferencedValueList.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final List<JsonValue> stringJsonValueList = toList(JsonValue.of("foo"), JsonValue.of("bar"));
        final List<JsonValue> numberJsonValueList = toList(JsonValue.of(1), JsonValue.of(2), JsonValue.of(3));
        final ImmutableJsonArray redArray = ImmutableJsonArray.of(stringJsonValueList);
        final ImmutableJsonArray blackArray = ImmutableJsonArray.of(numberJsonValueList);
        final ImmutableJsonArray.SoftReferencedValueList
                redValueList = ImmutableJsonArray.SoftReferencedValueList.of(stringJsonValueList);
        final ImmutableJsonArray.SoftReferencedValueList
                blackValueList = ImmutableJsonArray.SoftReferencedValueList.of(numberJsonValueList);

        EqualsVerifier.forClass(ImmutableJsonArray.class)
                .withPrefabValues(ImmutableJsonArray.class, redArray, blackArray)
                .withPrefabValues(ImmutableJsonArray.SoftReferencedValueList.class, redValueList, blackValueList)
                .withNonnullFields("valueList")
                .verify();
    }

    private static List<JsonValue> toList(final JsonValue... jsonValue) {
        final List<JsonValue> result = new ArrayList<>(jsonValue.length);
        Collections.addAll(result, jsonValue);
        return result;
    }

    @Test
    public void twoParsedArraysFromSameStringHaveSameHashCode() {
        final String jsonArrayString =
                "[\"latitude\", 44.673856, \"longitude\", 8.261719, \"maker\", \"ACME\"]";
        final Function<String, JsonValue> parser = JsonValueParser.fromString();

        final JsonValue parsedFirst = parser.apply(jsonArrayString);
        final JsonValue parsedSecond = parser.apply(jsonArrayString);

        assertThat(parsedFirst).isInstanceOf(ImmutableJsonArray.class);
        assertThat(parsedSecond).isInstanceOf(ImmutableJsonArray.class);
        assertThat(parsedFirst.hashCode()).hasSameHashCodeAs(parsedSecond.hashCode());
    }

    @Test
    public void emptyArrayIsEmpty() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        assertThat(underTest)
                .isArray()
                .isEmpty()
                .hasSize(0);
    }

    @Test
    public void getSizeReturnsExpected() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest).hasSize(3);
    }

    @Test
    public void asArrayReturnsSameObjectReference() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest.asArray()).isSameAs(underTest);
    }

    @Test
    public void arrayIsNothingElse() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        assertThat(underTest).isArray()
                .isNotNullLiteral()
                .isNotBoolean()
                .isNotNumber()
                .isNotString()
                .isNotObject();
        assertThat(underTest.isInt()).isFalse();
        assertThat(underTest.isLong()).isFalse();
        assertThat(underTest.isDouble()).isFalse();
    }

    @Test
    public void checkUnsupportedOperations() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest).doesNotSupport(JsonValue::asObject)
                .doesNotSupport(JsonValue::asBoolean)
                .doesNotSupport(JsonValue::asString)
                .doesNotSupport(JsonValue::asInt)
                .doesNotSupport(JsonValue::asLong)
                .doesNotSupport(JsonValue::asDouble);
    }

    @Test
    public void containsWorksAsExpected() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final List<Integer> expectedContainedValues = Arrays.asList(KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2);

        for (final Integer expectedContainedValue : expectedContainedValues) {
            assertThat(underTest.contains(newValue(expectedContainedValue))).isTrue();
        }
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddIntFurtherLongValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(0, (Integer) null);
    }

    @Test
    public void addIntReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final int firstIntValueToAdd = 1982;
        final int secondIntValueToAdd = 1983;
        final int thirdIntValueToAdd = 1984;
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd = underTest.add(firstIntValueToAdd, secondIntValueToAdd, thirdIntValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstIntValueToAdd)
                .contains(secondIntValueToAdd)
                .contains(thirdIntValueToAdd);
        assertThat(underTest).doesNotContain(firstIntValueToAdd)
                .doesNotContain(secondIntValueToAdd)
                .doesNotContain(thirdIntValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherLongValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(Long.MAX_VALUE, (Long) null);
    }

    @Test
    public void addLongReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final long firstLongValueToAdd = 1982L;
        final long secondLongValueToAdd = 1983L;
        final long thirdLongValueToAdd = 1984L;
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd =
                underTest.add(firstLongValueToAdd, secondLongValueToAdd, thirdLongValueToAdd);

        assertThat(afterAdd)
                .isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstLongValueToAdd)
                .contains(secondLongValueToAdd)
                .contains(thirdLongValueToAdd);
        assertThat(underTest).doesNotContain(firstLongValueToAdd)
                .doesNotContain(secondLongValueToAdd)
                .doesNotContain(thirdLongValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherDoubleValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(1.0D, null);
    }

    @Test
    public void addDoubleReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final double firstDoubleValueToAdd = 1982.01D;
        final double secondDoubleValueToAdd = 1983.02D;
        final double thirdDoubleValueToAdd = 1984.23D;
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd =
                underTest.add(firstDoubleValueToAdd, secondDoubleValueToAdd, thirdDoubleValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstDoubleValueToAdd)
                .contains(secondDoubleValueToAdd)
                .contains(thirdDoubleValueToAdd);
        assertThat(underTest).doesNotContain(firstDoubleValueToAdd)
                .doesNotContain(secondDoubleValueToAdd)
                .doesNotContain(thirdDoubleValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherBooleanValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(true, null);
    }

    @Test
    public void addBooleanReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final boolean firstBooleanValueToAdd = true;
        final boolean secondBooleanValueToAdd = false;
        final boolean thirdBooleanValueToAdd = true;
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd =
                underTest.add(firstBooleanValueToAdd, secondBooleanValueToAdd, thirdBooleanValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstBooleanValueToAdd)
                .contains(secondBooleanValueToAdd)
                .contains(thirdBooleanValueToAdd);
        assertThat(underTest).doesNotContain(firstBooleanValueToAdd)
                .doesNotContain(secondBooleanValueToAdd)
                .doesNotContain(thirdBooleanValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullStringValue() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add((String) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherStringValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add("", null);
    }

    @Test
    public void addStringReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final String firstStringValueToAdd = "1982.01D";
        final String secondStringValueToAdd = "1983.02D";
        final String thirdStringValueToAdd = "1984.23D";
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd =
                underTest.add(firstStringValueToAdd, secondStringValueToAdd, thirdStringValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstStringValueToAdd)
                .contains(secondStringValueToAdd)
                .contains(thirdStringValueToAdd);
        assertThat(underTest).doesNotContain(firstStringValueToAdd)
                .doesNotContain(secondStringValueToAdd)
                .doesNotContain(thirdStringValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullJsonValue() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add((JsonValue) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherJsonValues() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(mock(JsonValue.class), null);
    }

    @Test
    public void addJsonValueReturnsDisjointArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final JsonValue firstJsonValueToAdd = newValue("1982.01D");
        final JsonValue secondJsonValueToAdd = newValue("1983.02D");
        final JsonValue thirdJsonValueToAdd = newValue("1984.23D");
        final int expectedSize = underTest.getSize() + 3;
        final ImmutableJsonArray afterAdd =
                underTest.add(firstJsonValueToAdd, secondJsonValueToAdd, thirdJsonValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest)
                .hasSize(expectedSize)
                .contains(firstJsonValueToAdd)
                .contains(secondJsonValueToAdd)
                .contains(thirdJsonValueToAdd);
        assertThat(underTest).doesNotContain(firstJsonValueToAdd)
                .doesNotContain(secondJsonValueToAdd)
                .doesNotContain(thirdJsonValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }

    @Test
    public void indexOfReturnsExpected() {
        ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        underTest = underTest.add(KNOWN_INT_0, KNOWN_INT_0, KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2, KNOWN_INT_1);
        final int expectedIndex = 3;
        final int actualIndex = underTest.indexOf(newValue(KNOWN_INT_1));

        assertThat(actualIndex).isEqualTo(expectedIndex);
    }

    @Test
    public void iteratorWorksAsExpected() {
        final ImmutableJsonArray jsonArray = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final List<JsonValue> expectedJsonValues = new ArrayList<>();
        expectedJsonValues.add(newValue(KNOWN_INT_0));
        expectedJsonValues.add(newValue(KNOWN_INT_1));
        expectedJsonValues.add(newValue(KNOWN_INT_2));

        final Iterator<JsonValue> underTest = jsonArray.iterator();
        int index = 0;
        while (underTest.hasNext()) {
            assertThat(index).isEqualTo(expectedJsonValues.indexOf(underTest.next()));
            index++;
        }
        final int actualSize = index;

        assertThat(actualSize).isEqualTo(expectedJsonValues.size());
    }

    @Test
    public void toStringReturnsExpected() {
        final String expectedString = IntStream.of(KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest.toString()).isEqualTo(expectedString);
    }

    @Test
    public void getValueAtInvalidIndex() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final Optional<JsonValue> jsonValue = underTest.get(-1);

        assertThat(jsonValue).isEmpty();
    }

    @Test
    public void getValueAtValidIndex() {
        final int index = 1;
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final Optional<JsonValue> jsonValue = underTest.get(index);

        assertThat(jsonValue).contains(KNOWN_INT_VALUE_LIST.get(index));
    }

    @Test
    public void validateSoftReferenceStrategy() throws IllegalAccessException, NoSuchFieldException {
        final ImmutableJsonArray jsonArray = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        assertInternalCachesAreAsExpected(jsonArray, true);

        final Field valueListField = jsonArray.getClass().getDeclaredField("valueList");
        valueListField.setAccessible(true);
        final ImmutableJsonArray.SoftReferencedValueList
                valueList = (ImmutableJsonArray.SoftReferencedValueList) valueListField.get(jsonArray);

        final Field softReferenceField = valueList.getClass().getDeclaredField("valuesReference");
        softReferenceField.setAccessible(true);
        SoftReference softReference = (SoftReference) softReferenceField.get(valueList);

        softReference.clear();

        assertThat(jsonArray.get(0)).isPresent();
    }

    private void assertInternalCachesAreAsExpected(final JsonArray jsonArray, final boolean jsonExpected) {
        try {
            final Field valueListField = jsonArray.getClass().getDeclaredField("valueList");
            valueListField.setAccessible(true);
            final ImmutableJsonArray.SoftReferencedValueList
                    valueList = (ImmutableJsonArray.SoftReferencedValueList) valueListField.get(jsonArray);

            final Field jsonStringField = valueList.getClass().getDeclaredField("jsonArrayStringRepresentation");
            jsonStringField.setAccessible(true);
            String jsonString = (String) jsonStringField.get(valueList);

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
