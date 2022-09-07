/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link org.eclipse.ditto.base.model.acks.ImmutableAcknowledgementLabel}.
 */
public final class ImmutableAcknowledgementLabelTest {

    private static final String KNOWN_LABEL_VALUE = "KEEP-ALESIA";

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAcknowledgementLabel.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAcknowledgementLabel.class, areImmutable());
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableAcknowledgementLabel underTest = ImmutableAcknowledgementLabel.of(KNOWN_LABEL_VALUE);

        assertThat(underTest.toString()).hasToString(KNOWN_LABEL_VALUE);
    }

    @Test
    public void lengthReturnsExpected() {
        final ImmutableAcknowledgementLabel underTest = ImmutableAcknowledgementLabel.of(KNOWN_LABEL_VALUE);

        assertThat(underTest.length()).isEqualTo(KNOWN_LABEL_VALUE.length());
    }

    @Test
    public void charAtReturnsExpected() {
        final byte charIndex = 3;
        final ImmutableAcknowledgementLabel underTest = ImmutableAcknowledgementLabel.of(KNOWN_LABEL_VALUE);

        assertThat(underTest.charAt(charIndex)).isEqualTo(KNOWN_LABEL_VALUE.charAt(charIndex));
    }

    @Test
    public void subSequenceReturnsExpected() {
        final byte sequenceStart = 5;
        final byte sequenceEnd = 11;
        final ImmutableAcknowledgementLabel underTest = ImmutableAcknowledgementLabel.of(KNOWN_LABEL_VALUE);

        assertThat(underTest.subSequence(sequenceStart, sequenceEnd))
                .isEqualTo(KNOWN_LABEL_VALUE.subSequence(sequenceStart, sequenceEnd));
    }

    @Test
    public void compareToWorksAsExpected() {
        final ImmutableAcknowledgementLabel ackLabelAbc = ImmutableAcknowledgementLabel.of("abc");
        final ImmutableAcknowledgementLabel ackLabelDef = ImmutableAcknowledgementLabel.of("def");

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(ackLabelAbc.compareTo(ackLabelAbc))
                    .as("compare with equal")
                    .isZero();
            softly.assertThat(ackLabelAbc.compareTo(ackLabelDef))
                    .as("compare with greater")
                    .isNegative();
            softly.assertThat(ackLabelDef.compareTo(ackLabelAbc))
                    .as("compare with less")
                    .isPositive();
        }
    }

}
