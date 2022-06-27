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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link KeepAliveInterval}.
 */
public final class KeepAliveIntervalTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(KeepAliveInterval.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(KeepAliveInterval.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullDurationThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> KeepAliveInterval.of(null))
                .withMessage("The duration must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithNegativeDurationThrowsException() {
        final var negativeSeconds = KeepAliveInterval.MIN_INTERVAL_SECONDS - 1;

        assertThatExceptionOfType(IllegalKeepAliveIntervalSecondsException.class)
                .isThrownBy(() -> KeepAliveInterval.of(Duration.ofSeconds(negativeSeconds)))
                .withMessageEndingWith("but it was <%d>.", negativeSeconds)
                .withNoCause();
    }

    @Test
    public void ofWithTooLongDurationThrowsException() {
        final var exceedingSeconds = KeepAliveInterval.MAX_INTERVAL_SECONDS + 1;

        assertThatExceptionOfType(IllegalKeepAliveIntervalSecondsException.class)
                .isThrownBy(() -> KeepAliveInterval.of(Duration.ofSeconds(exceedingSeconds)))
                .withMessageEndingWith("but it was <%d>.", exceedingSeconds)
                .withNoCause();
    }

    @Test
    public void getSecondsReturnsExpected() throws IllegalKeepAliveIntervalSecondsException {
        final var seconds = 42L;
        final var underTest = KeepAliveInterval.of(Duration.ofSeconds(seconds));

        assertThat(underTest.getSeconds()).isEqualTo(seconds);
    }

    @Test
    public void zeroReturnsKeepAliveIntervalWithZeroSeconds() {
        final var underTest = KeepAliveInterval.zero();

        assertThat(underTest.getSeconds()).isZero();
    }

    @Test
    public void defaultKeepAliveIntervalHasExpectedSeconds() {
        final var underTest = KeepAliveInterval.defaultKeepAlive();

        assertThat(underTest.getSeconds()).isEqualTo(KeepAliveInterval.DEFAULT_INTERVAL_SECONDS);
    }

}