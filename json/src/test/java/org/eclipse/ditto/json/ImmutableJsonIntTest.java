/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.json.ImmutableJsonInt}.
 */
@RunWith(Parameterized.class)
public final class ImmutableJsonIntTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Integer> intValues() {
        return Arrays.asList(Integer.MIN_VALUE, Integer.MAX_VALUE, 1337);
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
        EqualsVerifier.forClass(ImmutableJsonInt.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void jsonIntEqualsJsonLongIfSameValue() {
        final ImmutableJsonLong longValue = ImmutableJsonLong.of(intValue);

        assertThat(underTest).isEqualTo(longValue);
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
    public void toStringReturnsExpected() {
        assertThat(underTest.toString()).isEqualTo(String.valueOf(intValue));
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
    public void isNotLong() {
        assertThat(underTest.isLong()).isFalse();
    }

    @Test
    public void isNotDouble() {
        assertThat(underTest.isDouble()).isFalse();
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
    public void tryToGetAsDouble() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.asDouble())
                .withMessage("This JSON value is not a double: %s", underTest)
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