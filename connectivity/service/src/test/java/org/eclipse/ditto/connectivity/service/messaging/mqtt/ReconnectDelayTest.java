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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ReconnectDelay}.
 */
public final class ReconnectDelayTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ReconnectDelay.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ReconnectDelay.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofOrLowerBoundaryWithNullDurationThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ReconnectDelay.ofOrLowerBoundary(null))
                .withMessage("The duration must not be null!")
                .withNoCause();
    }

    @Test
    public void ofOrLowerBoundaryReturnsReconnectDelayWithLowerBoundIfSpecifiedArgumentIsBelow() {
        final var reconnectDelay = ReconnectDelay.ofOrLowerBoundary(Duration.ofMillis(999L));

        assertThat(reconnectDelay.getDuration()).isEqualTo(ReconnectDelay.LOWER_BOUNDARY);
    }

    @Test
    public void ofOrLowerBoundaryReturnsReconnectDelayWithSpecifiedArgument() {
        final var duration = Duration.ofMillis(1_001L);
        final var reconnectDelay = ReconnectDelay.ofOrLowerBoundary(duration);

        assertThat(reconnectDelay.getDuration()).isEqualTo(duration);
    }

    @Test
    public void compareToWorksAsExpected() {
        final var reconnectDelayOneSecond = ReconnectDelay.ofOrLowerBoundary(Duration.ofSeconds(1L));
        final var reconnectDelayTwoSeconds = ReconnectDelay.ofOrLowerBoundary(Duration.ofSeconds(2L));

        assertThat(reconnectDelayOneSecond)
                .isEqualByComparingTo(reconnectDelayOneSecond)
                .isLessThan(reconnectDelayTwoSeconds);
        assertThat(reconnectDelayTwoSeconds)
                .isGreaterThan(reconnectDelayOneSecond);
    }

    @Test
    public void toStringReturnsStringRepresentationOfWrappedDuration() {
        final var duration = Duration.ofMillis(1_001L);
        final var reconnectDelay = ReconnectDelay.ofOrLowerBoundary(duration);

        assertThat(reconnectDelay).hasToString(duration.toString());
    }

}