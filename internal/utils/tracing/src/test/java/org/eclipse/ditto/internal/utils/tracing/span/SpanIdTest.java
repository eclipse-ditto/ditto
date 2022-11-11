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
package org.eclipse.ditto.internal.utils.tracing.span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SpanId}.
 */
public final class SpanIdTest {

    private static final String KNOWN_SPAN_IDENTIFIER = String.valueOf(UUID.randomUUID());

    @Test
    public void assertImmutability() {
        assertInstancesOf(SpanId.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SpanId.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SpanId.of(null))
                .withMessage("The identifier must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithEmptyNameThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpanId.of(""))
                .withMessage("The identifier must neither be empty nor blank.")
                .withNoCause();
    }

    @Test
    public void ofWithWhitespacesAsNameThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpanId.of("   "))
                .withMessage("The identifier must neither be empty nor blank.")
                .withNoCause();
    }

    @Test
    public void lengthReturnsExpected() {
        final var underTest = SpanId.of(KNOWN_SPAN_IDENTIFIER);

        assertThat(underTest.length()).isEqualTo(KNOWN_SPAN_IDENTIFIER.length());
    }

    @Test
    public void charAtReturnsExpected() {
        final var charIndex = 3;
        final var underTest = SpanId.of(KNOWN_SPAN_IDENTIFIER);

        assertThat(underTest.charAt(charIndex)).isEqualTo(KNOWN_SPAN_IDENTIFIER.charAt(charIndex));
    }

    @Test
    public void subSequenceReturnsExpected() {
        final var startIndex = 0;
        final var endIndex = 3;
        final var underTest = SpanId.of(KNOWN_SPAN_IDENTIFIER);

        assertThat(underTest.subSequence(startIndex, endIndex))
                .isEqualTo(KNOWN_SPAN_IDENTIFIER.subSequence(startIndex, endIndex));
    }

    @Test
    public void getEmptyInstanceReturnsEmpty() {
        final var underTest = SpanId.empty();

        assertThat(underTest.isEmpty()).isTrue();
    }

    @Test
    public void toStringReturnsExpected() {
        final var underTest = SpanId.of(KNOWN_SPAN_IDENTIFIER);

        assertThat((CharSequence) underTest).hasToString(KNOWN_SPAN_IDENTIFIER);
    }

    @Test
    public void compareToWorksAsExpected() {
        final var operationNameA = SpanId.of("span_a");
        final var operationNameB = SpanId.of("span_b");

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(operationNameA.compareTo(operationNameA)).as("compare to self").isZero();
            softly.assertThat(operationNameA.compareTo(operationNameB)).as("compare to greater").isNegative();
            softly.assertThat(operationNameB.compareTo(operationNameA)).as("compare to less").isPositive();
        }
    }


}