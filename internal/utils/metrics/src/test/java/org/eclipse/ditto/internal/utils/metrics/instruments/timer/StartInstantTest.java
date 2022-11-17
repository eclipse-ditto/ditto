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
package org.eclipse.ditto.internal.utils.metrics.instruments.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.assertj.core.data.Offset;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link StartInstant}.
 */
public final class StartInstantTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StartInstant.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StartInstant.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void startInstantNowIsApproximatelyCurrentSystemNanos() {
        final var underTest = StartInstant.now();
        final var currentNanos = System.nanoTime();

        assertThat(underTest.toNanos()).isCloseTo(currentNanos, Offset.offset(100_000L));
    }

    @Test
    public void toNanosReturnsPositiveValue() {
        final var underTest = StartInstant.now();

        assertThat(underTest.toNanos()).isPositive();
    }

    @Test
    public void toInstantReturnsConsistentResult() {
        final var underTest = StartInstant.now();

        assertThat(underTest.toInstant())
                .isNotNull()
                .isEqualTo(underTest.toInstant());
    }

    @SuppressWarnings("java:S2925")
    @Test
    public void compareToWorksAsExpected() throws InterruptedException {
        final var startInstantPast = StartInstant.now();
        Thread.sleep(500L);
        final var startInstantRecent = StartInstant.now();

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(startInstantPast.compareTo(startInstantPast)).as("compare to self").isZero();
            softly.assertThat(startInstantPast.compareTo(startInstantRecent)).as("compare to greater").isNegative();
            softly.assertThat(startInstantRecent.compareTo(startInstantPast)).as("compare to less").isPositive();
        }
    }

}