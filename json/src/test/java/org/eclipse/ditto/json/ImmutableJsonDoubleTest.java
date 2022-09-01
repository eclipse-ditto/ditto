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
 * Unit test for {@link org.eclipse.ditto.json.ImmutableJsonDouble}.
 */
@RunWith(Enclosed.class)
public final class ImmutableJsonDoubleTest {

    @RunWith(Parameterized.class)
    public static class ParameterizedTests{

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Double> doubleValues() {
            return Arrays.asList(Double.NEGATIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY,
                    0D, 0.0D, -0D, -0.0D, 13.3742D, 1.081542E124D);
        }

        @Parameterized.Parameter
        public double doubleValue;

        private ImmutableJsonDouble underTest;

        @Before
        public void setUp() {
            underTest = ImmutableJsonDouble.of(doubleValue);
        }

        @Test
        public void assertImmutability() {
            assertInstancesOf(ImmutableJsonDouble.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            final Double red = 23.0D;
            final Double black = 42.23D;

            EqualsVerifier.forClass(ImmutableJsonDouble.class)
                    .withRedefinedSuperclass()
                    .usingGetClass()
                    .withNonnullFields("value")
                    .withPrefabValues(Number.class, red, black)
                    .verify();
        }

        @Test
        public void jsonDoubleEqualsJsonIntIfSameValue() {
            final int value = Integer.MAX_VALUE;
            final ImmutableJsonInt intValue = ImmutableJsonInt.of(value);
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(value);

            assertThat(underTest).isEqualTo(intValue);
        }

        @Test
        public void jsonDoubleHasSameHashCodeAsJsonIntIfSameValue() {
            final int value = Integer.MAX_VALUE;
            final ImmutableJsonInt intValue = ImmutableJsonInt.of(value);
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(value);

            assertThat(underTest.hashCode()).hasSameHashCodeAs(intValue.hashCode());
        }

        @Test
        public void jsonDoubleEqualsJsonLongIfSameValue() {
            final long value = Long.MAX_VALUE;
            final ImmutableJsonLong longValue = ImmutableJsonLong.of(value);
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(value);

            assertThat(underTest).isEqualTo(longValue);
        }

        @Test
        public void jsonDoubleHasSameHashCodeAsJsonLongIfSameValue() {
            final long value = Long.MAX_VALUE;
            final ImmutableJsonLong longValue = ImmutableJsonLong.of(value);
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(value);

            assertThat(underTest.hashCode()).hasSameHashCodeAs(longValue.hashCode());
        }

        @Test
        public void getValueReturnsExpected() {
            assertThat(underTest.getValue()).isEqualTo(doubleValue);
        }

        @Test
        public void asDoubleReturnsExpected() {
            assertThat(underTest.asDouble()).isEqualTo(doubleValue);
        }

        @Test
        public void toStringReturnsExpected() {
            assertThat(underTest.toString()).hasToString(String.valueOf(doubleValue));
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
            assertThat(underTest.isInt()).isEqualTo(isDoubleValueWithinIntegerRange());
        }

        @Test
        public void jsonDoubleWithFractionsIsNotInt() {
            final double doubleValue = 23.42D;
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(doubleValue);

            assertThat(underTest.isInt()).isFalse();
        }

        @Test
        public void jsonDoubleWithZeroFractionsWithinIntRangeIsEqualToSameInt() {
            final double doubleValueWithFractions = 23.0D;
            final int equivalentIntValue = 23;

            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(doubleValueWithFractions);

            assertThat(underTest.isDouble()).isTrue();
            assertThat(underTest.isInt()).isTrue();
            assertThat(underTest.isLong()).isTrue();
            assertThat(underTest.asInt()).isEqualTo(equivalentIntValue);
        }

        @Test
        public void isLongReturnsExpected() {
            if (isDoubleValueWithinIntegerRange() || isDoubleValueWithinLongRange()) {
                assertThat(underTest.isLong()).isTrue();
            } else {
                assertThat(underTest.isLong()).isFalse();
            }
        }

        @Test
        public void jsonDoubleWithFractionsIsNotLong() {
            final double doubleValue = 23.42D;
            final ImmutableJsonDouble underTest = ImmutableJsonDouble.of(doubleValue);

            assertThat(underTest.isLong()).isFalse();
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
            if (underTest.isInt()) {
                assertThat(underTest.asInt()).isEqualTo(Double.valueOf(doubleValue).intValue());
            } else {
                assertThatExceptionOfType(NumberFormatException.class)
                        .isThrownBy(() -> underTest.asInt())
                        .withMessage("This JSON value is not an int: %s", underTest)
                        .withNoCause();
            }
        }

        @Test
        public void getAsLongBehavesCorrectly() {
            if (underTest.isLong()) {
                assertThat(underTest.asLong()).isEqualTo(Double.valueOf(doubleValue).longValue());
            } else {
                assertThatExceptionOfType(NumberFormatException.class)
                        .isThrownBy(() -> underTest.asLong())
                        .withMessage("This JSON value is not a long: %s", underTest)
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

        private boolean isDoubleValueWithinIntegerRange() {
            return Integer.MIN_VALUE <= doubleValue && Integer.MAX_VALUE >= doubleValue && hasNoFraction();
        }

        private boolean hasNoFraction() {
            return 0 == doubleValue % 1;
        }

        private boolean isDoubleValueWithinLongRange() {
            return Long.MIN_VALUE <= doubleValue && Long.MAX_VALUE >= doubleValue && hasNoFraction();
        }
    }

}
