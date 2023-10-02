/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MessageExpiryInterval}.
 */
public final class MessageExpiryIntervalTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(MessageExpiryInterval.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MessageExpiryInterval.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithMaxValueReturnsExpected()
            throws IllegalMessageExpiryIntervalSecondsException {
        final MessageExpiryInterval underTest = MessageExpiryInterval.of(MessageExpiryInterval.MAX_INTERVAL_SECONDS);

        assertThat(underTest.getAsOptionalLong()).hasValue(MessageExpiryInterval.MAX_INTERVAL_SECONDS);
    }

    @Test
    public void ofWithNegativeOutOfBoundsValueThrowsException() {
        final long negativeOutOfBoundsSeconds = MessageExpiryInterval.MIN_INTERVAL_SECONDS - 1;

        assertThatExceptionOfType(IllegalMessageExpiryIntervalSecondsException.class)
                .isThrownBy(() -> MessageExpiryInterval.of(negativeOutOfBoundsSeconds))
                .withMessageEndingWith("but it was <%d>.", negativeOutOfBoundsSeconds)
                .withNoCause();
    }

    @Test
    public void ofWithPositiveOutOfBoundsValueThrowsException() {
        final long positiveOutOfBoundsSeconds = MessageExpiryInterval.MAX_INTERVAL_SECONDS + 1;

        assertThatExceptionOfType(IllegalMessageExpiryIntervalSecondsException.class)
                .isThrownBy(() -> MessageExpiryInterval.of(positiveOutOfBoundsSeconds))
                .withMessageEndingWith("but it was <%d>.", positiveOutOfBoundsSeconds)
                .withNoCause();
    }

    @Test
    public void emptyReturnsEmpty() {
        final MessageExpiryInterval underTest = MessageExpiryInterval.empty();

        assertThat(underTest.getAsOptionalLong()).isEmpty();
    }

}
