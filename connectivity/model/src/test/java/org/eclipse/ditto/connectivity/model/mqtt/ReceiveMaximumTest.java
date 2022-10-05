/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ReceiveMaximum}.
 */
public final class ReceiveMaximumTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ReceiveMaximum.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ReceiveMaximum.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testCompareTo() throws IllegalReceiveMaximumValueException {
        final ReceiveMaximum minimumReceiveMaximum = ReceiveMaximum.of(1);
        final ReceiveMaximum defaultReceiveMaximum = ReceiveMaximum.defaultReceiveMaximum();
        
        assertThat(minimumReceiveMaximum).isLessThan(defaultReceiveMaximum);
        assertThat(defaultReceiveMaximum).isGreaterThan(minimumReceiveMaximum);
    }

    @Test
    public void ofWithZeroThrowsException() {
        final int value = 0;

        Assertions.assertThatExceptionOfType(IllegalReceiveMaximumValueException.class)
                .isThrownBy(() -> ReceiveMaximum.of(value))
                .withMessage("Expected value to be within [%d, %d] but it was <%d>.",
                        ReceiveMaximum.MIN_VALUE,
                        ReceiveMaximum.MAX_VALUE,
                        value)
                .withNoCause();
    }

    @Test
    public void ofWithValueExceedingMaximumThrowsException() {
        final int value = ReceiveMaximum.MAX_VALUE + 1;

        Assertions.assertThatExceptionOfType(IllegalReceiveMaximumValueException.class)
                .isThrownBy(() -> ReceiveMaximum.of(value))
                .withMessage("Expected value to be within [%d, %d] but it was <%d>.",
                        ReceiveMaximum.MIN_VALUE,
                        ReceiveMaximum.MAX_VALUE,
                        value)
                .withNoCause();
    }

    @Test
    public void getValueReturnsExpectedValue() throws IllegalReceiveMaximumValueException {
        final int value = 42;
        final ReceiveMaximum underTest = ReceiveMaximum.of(value);

        assertThat(underTest.getValue()).isEqualTo(value);
    }

}