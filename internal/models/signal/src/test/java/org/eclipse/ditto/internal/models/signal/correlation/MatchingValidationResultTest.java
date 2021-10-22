/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signal.correlation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MatchingValidationResult}.
 */
public final class MatchingValidationResultTest {

    private static final String DETAIL_MESSAGE = "My detail message.";

    @Test
    public void assertImmutabilityForSuccess() {
        final var success = MatchingValidationResult.success();

        assertInstancesOf(success.getClass(), areImmutable());
    }

    @Test
    public void getSuccessInstanceReturnsNotNull() {
        assertThat(MatchingValidationResult.success()).isNotNull();
    }

    @Test
    public void successIsSuccess() {
        final var underTest = MatchingValidationResult.success();

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void getDetailMessageOnSuccessThrowsException() {
        final var underTest = MatchingValidationResult.success();

        assertThatIllegalStateException()
                .isThrownBy(underTest::getDetailMessageOrThrow)
                .withMessage("Validation was successful, hence there is no detail message.")
                .withNoCause();
    }

    @Test
    public void assertImmutabilityForFailure() {
        final var failure = MatchingValidationResult.failure(DETAIL_MESSAGE);

        assertInstancesOf(failure.getClass(), areImmutable());
    }

    @Test
    public void testHashCodeAndEqualsForFailure() {
        final var failure = MatchingValidationResult.failure(DETAIL_MESSAGE);

        EqualsVerifier.forClass(failure.getClass())
                .usingGetClass()
                .verify();
    }

    @Test
    public void getFailureInstanceReturnsNotNull() {
        assertThat(MatchingValidationResult.failure(DETAIL_MESSAGE)).isNotNull();
    }

    @Test
    public void getFailureInstanceWithNullDetailMessageThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> MatchingValidationResult.failure(null))
                .withMessage("The detailMessage must not be null!")
                .withNoCause();
    }

    @Test
    public void getFailureInstanceWithEmptyDetailMessageThrowsException() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> MatchingValidationResult.failure(""))
                .withMessage("The argument 'detailMessage' must not be empty!")
                .withNoCause();
    }

    @Test
    public void failureIsNotSuccess() {
        final var underTest = MatchingValidationResult.failure(DETAIL_MESSAGE);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void getDetailMessageReturnsDetailMessage() {
        final var underTest = MatchingValidationResult.failure(DETAIL_MESSAGE);

        assertThat(underTest.getDetailMessageOrThrow()).isEqualTo(DETAIL_MESSAGE);
    }

}