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
 * Unit test for {@link org.eclipse.ditto.json.ImmutableJsonInt}.
 */
@RunWith(Enclosed.class)
public final class ImmutableJsonIntTest {

    @RunWith(Parameterized.class)
    public static class ParameterizedTests {

        @Parameterized.Parameters(name = "{0}")
        public static Collection<Integer> intValues() {
            return Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 1337);
        }

        @Parameterized.Parameter
        public int intValue;

        private ImmutableJsonInt underTest;

        @Before
        public void setUp() {
            underTest = ImmutableJsonInt.of(intValue);
        }

        @Test
        public void assertImmutability() {
            assertInstancesOf(ImmutableJsonInt.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            final Integer red = intValue / 2;
            final Integer black = red + 1;

            EqualsVerifier.forClass(ImmutableJsonInt.class)
                    .withRedefinedSuperclass()
                    .usingGetClass()
                    .withNonnullFields("value")
                    .withPrefabValues(Number.class, red, black)
                    .verify();
        }

        @Test
        public void jsonIntEqualsJsonLongIfSameValue() {
            final ImmutableJsonLong longValue = ImmutableJsonLong.of(intValue);

            assertThat(underTest).isEqualTo(longValue);
        }

        @Test
        public void jsonIntHasSameHashCodeAsJsonLongIfSameValue() {
            final ImmutableJsonLong longValue = ImmutableJsonLong.of(intValue);

            assertThat(underTest.hashCode()).hasSameHashCodeAs(longValue.hashCode());
        }

        @Test
        public void jsonIntEqualsJsonDoubleIfSameValue() {
            final ImmutableJsonDouble doubleValue = ImmutableJsonDouble.of(intValue);

            assertThat(underTest).isEqualTo(doubleValue);
        }

        @Test
        public void jsonIntHasSameHashCodeAsJsonDoubleIfSameValue() {
            final ImmutableJsonDouble doubleValue = ImmutableJsonDouble.of(intValue);

            assertThat(underTest.hashCode()).hasSameHashCodeAs(doubleValue.hashCode());
        }

        @Test
        public void getValueReturnsExpected() {
            assertThat(underTest.getValue()).isEqualTo(intValue);
        }

        @Test
        public void asIntReturnsExpected() {
            assertThat(underTest.asInt()).isEqualTo(intValue);
        }

        @Test
        public void asLongReturnsExpected() {
            assertThat(underTest.asLong()).isEqualTo(intValue);
        }

        @Test
        public void asDoubleReturnsExpected() {
            assertThat(underTest.asDouble()).isEqualTo(intValue);
        }

        @Test
        public void toStringReturnsExpected() {
            assertThat(underTest.toString()).hasToString(String.valueOf(intValue));
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
        public void isInt() {
            assertThat(underTest.isInt()).isTrue();
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
    }

}
