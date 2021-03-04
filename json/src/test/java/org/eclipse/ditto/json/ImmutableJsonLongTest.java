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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonLong}.
 */
@RunWith(Enclosed.class)
public final class ImmutableJsonLongTest {

    @RunWith(Parameterized.class)
    public static class ParameterizedTests {

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Long> doubleValues() {
            return Arrays.asList(Long.MIN_VALUE, Long.MAX_VALUE, 0L, 23420815L);
        }

        @Parameterized.Parameter
        public long longValue;

        private ImmutableJsonLong underTest;

        @Before
        public void setUp() {
            underTest = ImmutableJsonLong.of(longValue);
        }

        @Test
        public void assertImmutability() {
            assertInstancesOf(ImmutableJsonLong.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            final Long red = longValue / 2;
            final Long black = red + 1;

            EqualsVerifier.forClass(ImmutableJsonLong.class)
                    .withRedefinedSuperclass()
                    .usingGetClass()
                    .withNonnullFields("value")
                    .withPrefabValues(Number.class, red, black)
                    .verify();
        }

        @Test
        public void jsonLongEqualsJsonIntIfSameValue() {
            final int value = Integer.MAX_VALUE;
            final ImmutableJsonInt intValue = ImmutableJsonInt.of(value);
            final ImmutableJsonLong underTest = ImmutableJsonLong.of(value);

            assertThat(underTest).isEqualTo(intValue);
        }

        @Test
        public void jsonLongHasSameHashCodeAsJsonIntIfSameValue() {
            final int value = Integer.MAX_VALUE;
            final ImmutableJsonInt intValue = ImmutableJsonInt.of(value);
            final ImmutableJsonLong underTest = ImmutableJsonLong.of(value);

            assertThat(underTest.hashCode()).isEqualTo(intValue.hashCode());
        }

        @Test
        public void jsonLongEqualsJsonDoubleIfSameValue() {
            final ImmutableJsonDouble doubleValue = ImmutableJsonDouble.of(longValue);

            assertThat(underTest).isEqualTo(doubleValue);
        }

        @Test
        public void jsonLongHasSameHashCodeAsJsonDoubleIfSameValue() {
            final ImmutableJsonDouble doubleValue = ImmutableJsonDouble.of(longValue);

            assertThat(underTest.hashCode()).isEqualTo(doubleValue.hashCode());
        }

        @Test
        public void getValueReturnsExpected() {
            assertThat(underTest.getValue()).isEqualTo(longValue);
        }

        @Test
        public void asLongReturnsExpected() {
            assertThat(underTest.asLong()).isEqualTo(longValue);
        }

        @Test
        public void asDoubleReturnsExpected() {
            assertThat(underTest.asDouble()).isEqualTo(longValue);
        }

        @Test
        public void toStringReturnsExpected() {
            assertThat(underTest.toString()).isEqualTo(String.valueOf(longValue));
        }

        @Test
        public void isNotNull() {
            assertThat(underTest.isNull()).isFalse();
        }

        @Test
        public void isNotBoolean() {
            assertThat(underTest.isBoolean()).isFalse();
        }

        @Test
        public void isNumber() {
            assertThat(underTest.isNumber()).isTrue();
        }

        @Test
        public void isIntReturnsExpected() {
            assertThat(underTest.isInt()).isEqualTo(isLongValueWithinIntegerRange());
        }

        @Test
        public void isLong() {
            assertThat(underTest.isLong()).isTrue();
        }

        @Test
        public void isDouble() {
            assertThat(underTest.isDouble()).isTrue();
        }

        @Test
        public void isNotString() {
            assertThat(underTest.isString()).isFalse();
        }

        @Test
        public void isNotObject() {
            assertThat(underTest.isObject()).isFalse();
        }

        @Test
        public void isNotArray() {
            assertThat(underTest.isArray()).isFalse();
        }

        @Test
        public void tryToGetAsBoolean() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> underTest.asBoolean())
                    .withMessage("This JSON value is not a boolean: %s", underTest)
                    .withNoCause();
        }

        @Test
        public void getAsIntBehavesCorrectly() {
            if (isLongValueWithinIntegerRange()) {
                assertThat(underTest.asInt()).isEqualTo(Long.valueOf(longValue).intValue());
            } else {
                assertThatExceptionOfType(NumberFormatException.class)
                        .isThrownBy(() -> underTest.asInt())
                        .withMessage("This JSON value is not an int: %s", underTest)
                        .withNoCause();
            }
        }

        @Test
        public void tryToGetAsString() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> underTest.asString())
                    .withMessage("This JSON value is not a string: %s", underTest)
                    .withNoCause();
        }

        @Test
        public void tryToGetAsObject() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> underTest.asObject())
                    .withMessage("This JSON value is not an object: %s", underTest)
                    .withNoCause();
        }

        @Test
        public void tryToGetAsArray() {
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> underTest.asArray())
                    .withMessage("This JSON value is not an array: %s", underTest)
                    .withNoCause();
        }

        private boolean isLongValueWithinIntegerRange() {
            return Integer.MIN_VALUE <= longValue && Integer.MAX_VALUE >= longValue;
        }
    }

}
