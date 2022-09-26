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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SessionExpiryInterval}.
 */
public final class SessionExpiryIntervalTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(SessionExpiryInterval.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SessionExpiryInterval.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getInstanceWithDefaultSessionExpiryIntervalReturnsExpected() {
        final SessionExpiryInterval underTest = SessionExpiryInterval.defaultSessionExpiryInterval();

        assertThat(underTest).isEqualTo(SessionExpiryInterval.defaultSessionExpiryInterval());
    }

    @Test
    public void ofWithNullDurationThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SessionExpiryInterval.of(null))
                .withMessage("The duration must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithMaxValueReturnsExpected() throws IllegalSessionExpiryIntervalSecondsException {
        final Duration duration = Duration.ofSeconds(SessionExpiryInterval.MAX_INTERVAL_SECONDS);
        final SessionExpiryInterval underTest = SessionExpiryInterval.of(duration);

        assertThat(underTest.getSeconds()).isEqualTo(duration.getSeconds());
    }

    @Test
    public void ofWithNegativeOutOfBoundsDurationThrowsException() {
        final byte negativeOutOfBoundsSeconds = SessionExpiryInterval.MIN_INTERVAL_SECONDS - 1;

        assertThatExceptionOfType(IllegalSessionExpiryIntervalSecondsException.class)
                .isThrownBy(() -> SessionExpiryInterval.of(Duration.ofSeconds(negativeOutOfBoundsSeconds)))
                .withMessageEndingWith("but it was <%d>.", negativeOutOfBoundsSeconds)
                .withNoCause();
    }

    @Test
    public void ofWithPositiveOUtOfBoundsDurationThrowsException() {
        final long positiveOutOfBoundsSeconds = SessionExpiryInterval.MAX_INTERVAL_SECONDS + 1;

        assertThatExceptionOfType(IllegalSessionExpiryIntervalSecondsException.class)
                .isThrownBy(() -> SessionExpiryInterval.of(Duration.ofSeconds(positiveOutOfBoundsSeconds)))
                .withMessageEndingWith("but it was <%d>.", positiveOutOfBoundsSeconds)
                .withNoCause();
    }

}