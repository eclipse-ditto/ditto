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

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.json.ImmutableJsonBooleanTest}.
 */
public final class ImmutableJsonBooleanTest {

    private ImmutableJsonBoolean underTest;

    @Before
    public void setUp() {
        underTest = ImmutableJsonBoolean.of(true);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonBoolean.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonBoolean.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void asBooleanReturnsExpected() {
        final boolean javaBoolean = true;
        final ImmutableJsonBoolean underTest = ImmutableJsonBoolean.of(javaBoolean);

        assertThat(underTest.asBoolean()).isEqualTo(javaBoolean);
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableJsonBoolean underTest = ImmutableJsonBoolean.FALSE;

        assertThat(underTest.toString()).hasToString("false");
    }

    @Test
    public void isNotNull() {
        assertThat(underTest.isNull()).isFalse();
    }

    @Test
    public void isBoolean() {
        assertThat(underTest.isBoolean()).isTrue();
    }

    @Test
    public void isNotNumber() {
        assertThat(underTest.isNumber()).isFalse();
        assertThat(underTest.isInt()).isFalse();
        assertThat(underTest.isLong()).isFalse();
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
    public void tryToGetAsInt() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.asInt())
                .withMessage("This JSON value is not an int: %s", underTest)
                .withNoCause();
    }

    @Test
    public void tryToGetAsLong() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.asLong())
                .withMessage("This JSON value is not a long: %s", underTest)
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
