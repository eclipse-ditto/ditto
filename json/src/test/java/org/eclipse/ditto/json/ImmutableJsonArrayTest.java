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

        EqualsVerifier.forClass(ImmutableJsonArray.class)
                .withIgnoredFields("stringRepresentation")
                .withRedefinedSuperclass()
                .suppress(Warning.NULL_FIELDS)
                .withPrefabValues(SoftReference.class, red, black)
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceFromNullJsonArray() {
        ImmutableJsonArray.of(null);
    }

    @Test
    public void emptyArrayIsEmpty() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();

        assertThat(underTest).isArray();
        assertThat(underTest).isEmpty();
        assertThat(underTest).hasSize(0);
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

        assertThat(underTest).isArray();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotNumber();
        assertThat(underTest).isNotString();
        assertThat(underTest).isNotObject();
    }

    @Test
    public void checkUnsupportedOperations() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.of(KNOWN_INT_VALUE_LIST);

        assertThat(underTest).doesNotSupport(JsonValue::asObject);
        assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        assertThat(underTest).doesNotSupport(JsonValue::asString);
        assertThat(underTest).doesNotSupport(JsonValue::asInt);
        assertThat(underTest).doesNotSupport(JsonValue::asLong);
        assertThat(underTest).doesNotSupport(JsonValue::asDouble);
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
        final JsonArray afterAdd = underTest.add(firstIntValueToAdd, secondIntValueToAdd, thirdIntValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstIntValueToAdd);
        assertThat(afterAdd).contains(secondIntValueToAdd);
        assertThat(afterAdd).contains(thirdIntValueToAdd);
        assertThat(underTest).doesNotContain(firstIntValueToAdd);
        assertThat(underTest).doesNotContain(secondIntValueToAdd);
        assertThat(underTest).doesNotContain(thirdIntValueToAdd);

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
        final ImmutableJsonArray afterAdd = underTest.add(firstLongValueToAdd, secondLongValueToAdd, thirdLongValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstLongValueToAdd);
        assertThat(afterAdd).contains(secondLongValueToAdd);
        assertThat(afterAdd).contains(thirdLongValueToAdd);
        assertThat(underTest).doesNotContain(firstLongValueToAdd);
        assertThat(underTest).doesNotContain(secondLongValueToAdd);
        assertThat(underTest).doesNotContain(thirdLongValueToAdd);

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

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstDoubleValueToAdd);
        assertThat(afterAdd).contains(secondDoubleValueToAdd);
        assertThat(afterAdd).contains(thirdDoubleValueToAdd);
        assertThat(underTest).doesNotContain(firstDoubleValueToAdd);
        assertThat(underTest).doesNotContain(secondDoubleValueToAdd);
        assertThat(underTest).doesNotContain(thirdDoubleValueToAdd);

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
    public void addBooleanReturnsDisjunctArray() {
        final ImmutableJsonArray underTest = ImmutableJsonArray.empty();
        final boolean firstBooleanValueToAdd = true;
        final boolean secondBooleanValueToAdd = false;
        final boolean thirdBooleanValueToAdd = true;
        final int expectedSize = underTest.getSize() + 3;
        final JsonArray afterAdd =
                underTest.add(firstBooleanValueToAdd, secondBooleanValueToAdd, thirdBooleanValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstBooleanValueToAdd);
        assertThat(afterAdd).contains(secondBooleanValueToAdd);
        assertThat(afterAdd).contains(thirdBooleanValueToAdd);
        assertThat(underTest).doesNotContain(firstBooleanValueToAdd);
        assertThat(underTest).doesNotContain(secondBooleanValueToAdd);
        assertThat(underTest).doesNotContain(thirdBooleanValueToAdd);

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

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstStringValueToAdd);
        assertThat(afterAdd).contains(secondStringValueToAdd);
        assertThat(afterAdd).contains(thirdStringValueToAdd);
        assertThat(underTest).doesNotContain(firstStringValueToAdd);
        assertThat(underTest).doesNotContain(secondStringValueToAdd);
        assertThat(underTest).doesNotContain(thirdStringValueToAdd);

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
        final ImmutableJsonArray afterAdd = underTest.add(firstJsonValueToAdd, secondJsonValueToAdd, thirdJsonValueToAdd);

        assertThat(afterAdd).isNotSameAs(underTest);
        assertThat(afterAdd).hasSize(expectedSize);
        assertThat(afterAdd).contains(firstJsonValueToAdd);
        assertThat(afterAdd).contains(secondJsonValueToAdd);
        assertThat(afterAdd).contains(thirdJsonValueToAdd);
        assertThat(underTest).doesNotContain(firstJsonValueToAdd);
        assertThat(underTest).doesNotContain(secondJsonValueToAdd);
        assertThat(underTest).doesNotContain(thirdJsonValueToAdd);

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

}
