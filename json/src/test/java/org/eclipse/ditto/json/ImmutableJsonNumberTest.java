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
import static org.junit.Assert.fail;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.lang.ref.SoftReference;
import java.util.function.Supplier;

import org.junit.Test;

import com.eclipsesource.json.Json;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ImmutableJsonNumber}.
 */
public final class ImmutableJsonNumberTest {

    private static <T extends Number> void assertNumberFormatException(final Supplier<T> sup) {
        try {
            sup.get();
            fail("Expected a NumberFormatException to be thrown.");
        } catch (final Exception e) {
            assertThat(e).isInstanceOf(NumberFormatException.class);
        }
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonNumber.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonValue> red = new SoftReference<>(JsonFactory.newValue(23));
        final SoftReference<JsonValue> black = new SoftReference<>(JsonFactory.newValue(42.0D));

        EqualsVerifier.forClass(ImmutableJsonNumber.class) //
                .withIgnoredFields("stringRepresentation") //
                .withRedefinedSuperclass() //
                .withPrefabValues(SoftReference.class, red, black) //
                .suppress(Warning.REFERENCE_EQUALITY) //
                .verify();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateInstanceFromStringJsonValue() {
        final com.eclipsesource.json.JsonValue jsonString = com.eclipsesource.json.Json.value("foo");
        ImmutableJsonNumber.of(jsonString);
    }

    @Test
    public void jsonNumberValueForIntBehavesAsExpected() {
        final int intValue = 42;
        final com.eclipsesource.json.JsonValue jsonValue = com.eclipsesource.json.Json.value(intValue);
        final JsonValue underTest = ImmutableJsonNumber.of(jsonValue);

        assertThat(underTest).isNumber();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotString();
        assertThat(underTest).isNotObject();
        assertThat(underTest).isNotArray();
        assertThat(underTest.asInt()).isEqualTo(intValue);
        assertThat(underTest.toString()).isEqualTo(String.valueOf(intValue));
        assertThat(underTest.asDouble()).isEqualTo((double) intValue);
        assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        assertThat(underTest).doesNotSupport(JsonValue::asString);
        assertThat(underTest).doesNotSupport(JsonValue::asArray);
        assertThat(underTest).doesNotSupport(JsonValue::asObject);
    }

    @Test
    public void jsonNumberValueForDoubleBehavesAsExpected() {
        final double doubleValue = 23.42;
        final com.eclipsesource.json.JsonValue jsonValue = com.eclipsesource.json.Json.value(doubleValue);
        final JsonValue underTest = ImmutableJsonNumber.of(jsonValue);

        assertThat(underTest).isNumber();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotString();
        assertThat(underTest).isNotObject();
        assertThat(underTest).isNotArray();
        assertThat(underTest.asDouble()).isEqualTo(doubleValue);
        assertThat(underTest.toString()).isEqualTo(String.valueOf(doubleValue));
        assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        assertThat(underTest).doesNotSupport(JsonValue::asString);
        assertThat(underTest).doesNotSupport(JsonValue::asArray);
        assertThat(underTest).doesNotSupport(JsonValue::asObject);
        assertNumberFormatException(underTest::asInt);
        assertNumberFormatException(underTest::asLong);
    }

    @Test
    public void toStringOfZeroDoubleValueReturnsExpected() {
        final double doubleValue = 0.0D;
        final com.eclipsesource.json.JsonValue jsonValue = Json.value(doubleValue);
        final JsonValue underTest = ImmutableJsonNumber.of(jsonValue);

        assertThat(underTest.toString()).isEqualTo("0");
    }

    @Test
    public void toStringOfZeroIntValueReturnsExpected() {
        final int intValue = 0;
        final com.eclipsesource.json.JsonValue jsonValue = Json.value(intValue);
        final ImmutableJsonNumber underTest = ImmutableJsonNumber.of(jsonValue);

        assertThat(underTest.toString()).isEqualTo("0");
    }

    @Test
    public void zeroIntIsEqualToZeroDouble() {
        final ImmutableJsonNumber doubleZero = ImmutableJsonNumber.of(Json.value(0.0D));
        final ImmutableJsonNumber intZero = ImmutableJsonNumber.of(Json.value(0));

        assertThat(doubleZero).isEqualTo(intZero);
    }

}
