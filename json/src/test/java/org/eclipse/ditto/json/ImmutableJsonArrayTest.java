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

import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

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
        assertInstancesOf(ImmutableJsonArray.class, areImmutable(), provided(JsonValue.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonObject> red = new SoftReference<>(JsonFactory.newObject("{\"foo\": 1}"));
        final SoftReference<JsonObject> black = new SoftReference<>(JsonFactory.newObject("{\"foo\": 2}"));

        EqualsVerifier.forClass(ImmutableJsonArray.class) //
                .withIgnoredFields("stringRepresentation") //
                .withRedefinedSuperclass() //
                .suppress(Warning.NULL_FIELDS) //
                .withPrefabValues(SoftReference.class, red, black) //
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceFromNullJsonArray() {
        ImmutableJsonArray.of(null);
    }


    @Test
    public void emptyArrayIsEmpty() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat(underTest).isEmpty();
        DittoJsonAssertions.assertThat(underTest).hasSize(0);
    }


    @Test
    public void getSizeReturnsExpected() {
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        DittoJsonAssertions.assertThat(underTest).hasSize(3);
    }


    @Test
    public void asArrayReturnsSameObjectReference() {
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest.asArray()).isSameAs(underTest);
    }


    @Test
    public void arrayIsNothingElse() {
        final JsonValue underTest = ImmutableJsonArray.empty();

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat(underTest).isNotNullLiteral();
        DittoJsonAssertions.assertThat(underTest).isNotBoolean();
        DittoJsonAssertions.assertThat(underTest).isNotNumber();
        DittoJsonAssertions.assertThat(underTest).isNotString();
        DittoJsonAssertions.assertThat(underTest).isNotObject();
    }


    @Test
    public void checkUnsupportedOperations() {
        final JsonValue underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asObject);
        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asString);
        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asInt);
        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asLong);
        DittoJsonAssertions.assertThat(underTest).doesNotSupport(JsonValue::asDouble);
    }


    @Test
    public void containsWorksAsExpected() {
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final List<Integer> expectedContainedValues = Arrays.asList(KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2);

        for (final Integer expectedContainedValue : expectedContainedValues) {
            assertThat(underTest.contains(newValue(expectedContainedValue))).isTrue();
        }
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddIntFurtherLongValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(0, (Integer) null);
    }


    @Test
    public void addIntReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final int firstIntValueToAdd = 1982;
        final int secondIntValueToAdd = 1983;
        final int thirdIntValueToAdd = 1984;
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd = underTest.add(firstIntValueToAdd, secondIntValueToAdd, thirdIntValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstIntValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondIntValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdIntValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstIntValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondIntValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdIntValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherLongValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(Long.MAX_VALUE, (Long) null);
    }


    @Test
    public void addLongReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final long firstLongValueToAdd = 1982L;
        final long secondLongValueToAdd = 1983L;
        final long thirdLongValueToAdd = 1984L;
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd = underTest.add(firstLongValueToAdd, secondLongValueToAdd, thirdLongValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstLongValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondLongValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdLongValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstLongValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondLongValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdLongValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherDoubleValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(1.0D, null);
    }


    @Test
    public void addDoubleReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final double firstDoubleValueToAdd = 1982.01D;
        final double secondDoubleValueToAdd = 1983.02D;
        final double thirdDoubleValueToAdd = 1984.23D;
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd = underTest.add(firstDoubleValueToAdd, secondDoubleValueToAdd, thirdDoubleValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstDoubleValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondDoubleValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdDoubleValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstDoubleValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondDoubleValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdDoubleValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherBooleanValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(true, null);
    }


    @Test
    public void addBooleanReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final boolean firstBooleanValueToAdd = true;
        final boolean secondBooleanValueToAdd = false;
        final boolean thirdBooleanValueToAdd = true;
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd =
                underTest.add(firstBooleanValueToAdd, secondBooleanValueToAdd, thirdBooleanValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstBooleanValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondBooleanValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdBooleanValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstBooleanValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondBooleanValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdBooleanValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullStringValue() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add((String) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherStringValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add("", null);
    }


    @Test
    public void addStringReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final String firstStringValueToAdd = "1982.01D";
        final String secondStringValueToAdd = "1983.02D";
        final String thirdStringValueToAdd = "1984.23D";
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd = underTest.add(firstStringValueToAdd, secondStringValueToAdd, thirdStringValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstStringValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondStringValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdStringValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstStringValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondStringValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdStringValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullJsonValue() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add((JsonValue) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToAddNullFurtherJsonValues() {
        final JsonArray underTest = ImmutableJsonArray.empty();

        underTest.add(mock(JsonValue.class), null);
    }


    @Test
    public void addJsonValueReturnsDisjunctArray() {
        final JsonArray underTest = ImmutableJsonArray.empty();
        final JsonValue firstJsonValueToAdd = newValue("1982.01D");
        final JsonValue secondJsonValueToAdd = newValue("1983.02D");
        final JsonValue thirdJsonValueToAdd = newValue("1984.23D");
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd = underTest.add(firstJsonValueToAdd, secondJsonValueToAdd, thirdJsonValueToAdd);

        DittoJsonAssertions.assertThat(afterAdd).isNotSameAs(underTest);
        DittoJsonAssertions.assertThat(afterAdd).hasSize(expectedSize);
        DittoJsonAssertions.assertThat(afterAdd).contains(firstJsonValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(secondJsonValueToAdd);
        DittoJsonAssertions.assertThat(afterAdd).contains(thirdJsonValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(firstJsonValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(secondJsonValueToAdd);
        DittoJsonAssertions.assertThat(underTest).doesNotContain(thirdJsonValueToAdd);

        final JsonValue otherValueToAddToOriginalJsonArray = newValue(KNOWN_INT_0);
        underTest.add(otherValueToAddToOriginalJsonArray);

        DittoJsonAssertions.assertThat(afterAdd).doesNotContain(otherValueToAddToOriginalJsonArray);
    }


    @Test
    public void indexOfReturnsExpected() {
        JsonArray underTest = ImmutableJsonArray.empty();
        underTest = underTest.add(KNOWN_INT_0, KNOWN_INT_0, KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2, KNOWN_INT_1);
        final int expectedIndex = 3;
        final int actualIndex = underTest.indexOf(newValue(KNOWN_INT_1));

        assertThat(actualIndex).isEqualTo(expectedIndex);
    }


    @Test
    public void iteratorWorksAsExpected() {
        final JsonArray jsonArray = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
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
        final String expectedString = IntStream.of(KNOWN_INT_0, KNOWN_INT_1, KNOWN_INT_2).mapToObj(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest.toString()).isEqualTo(expectedString);
    }


    @Test
    public void getValueAtInvalidIndex() {
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final Optional<JsonValue> jsonValue = underTest.get(-1);

        assertThat(jsonValue).isEmpty();
    }


    @Test
    public void getValueAtValidIndex() {
        final int index = 1;
        final JsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);
        final Optional<JsonValue> jsonValue = underTest.get(index);

        assertThat(jsonValue).contains(KNOWN_INT_VALUE_LIST.get(index));
    }

}
