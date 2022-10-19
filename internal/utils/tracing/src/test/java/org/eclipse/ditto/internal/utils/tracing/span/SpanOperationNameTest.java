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

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SpanOperationName}.
 */
public final class SpanOperationNameTest {

    private static final String KNOWN_NAME = "my_operation";

    @Test
    public void assertImmutability() {
        assertInstancesOf(SpanOperationName.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SpanOperationName.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SpanOperationName.of(null))
                .withMessage("The name must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithEmptyNameThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpanOperationName.of(""))
                .withMessage("The name must neither be empty nor blank.")
                .withNoCause();
    }

    @Test
    public void ofWithWhitespacesAsNameThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SpanOperationName.of("   "))
                .withMessage("The name must neither be empty nor blank.")
                .withNoCause();
    }

    @Test
    public void ofTrimsLeadingAndTrailingWhitespaces() {
        final var name = "  my_operation   ";
        final var underTest = SpanOperationName.of(name);

        assertThat(underTest.toString()).isEqualTo("my_operation");
    }

    @Test
    public void lengthReturnsExpected() {
        final var underTest = SpanOperationName.of(KNOWN_NAME);

        assertThat(underTest.length()).isEqualTo(KNOWN_NAME.length());
    }

    @Test
    public void charAtReturnsExpected() {
        final var charIndex = 3;
        final var underTest = SpanOperationName.of(KNOWN_NAME);

        assertThat(underTest.charAt(charIndex)).isEqualTo(KNOWN_NAME.charAt(charIndex));
    }

    @Test
    public void subSequenceReturnsExpected() {
        final var startIndex = 0;
        final var endIndex = 3;
        final var underTest = SpanOperationName.of(KNOWN_NAME);

        assertThat(underTest.subSequence(startIndex, endIndex)).isEqualTo(KNOWN_NAME.subSequence(startIndex, endIndex));
    }

    @Test
    public void toStringReturnsExpected() {
        final var underTest = SpanOperationName.of(KNOWN_NAME);

        assertThat((CharSequence) underTest).hasToString(KNOWN_NAME);
    }

    @Test
    public void compareToWorksAsExpected() {
        final var operationNameA = SpanOperationName.of("operation_a");
        final var operationNameB = SpanOperationName.of("operation_b");

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(operationNameA.compareTo(operationNameA)).as("compare to self").isZero();
            softly.assertThat(operationNameA.compareTo(operationNameB)).as("compare to greater").isNegative();
            softly.assertThat(operationNameB.compareTo(operationNameA)).as("compare to less").isPositive();
        }
    }

}