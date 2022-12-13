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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EmptyPreparedSpan}.
 */
public final class EmptyPreparedSpanTest {

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void assertImmutability() {
        assertInstancesOf(EmptyPreparedSpan.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EmptyPreparedSpan.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullOperationNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> EmptyPreparedSpan.newInstance(null))
                .withMessage("The operationName must not be null!")
                .withNoCause();
    }

    @Test
    public void operationNameIsPropagatedToStartedSpan() {
        final var operationName = SpanOperationName.of(testName.getMethodName());
        final var underTest = EmptyPreparedSpan.newInstance(operationName);

        final var startedSpan = underTest.start();

        assertThat((CharSequence) startedSpan.getOperationName()).isEqualTo(operationName);
    }

    @Test
    public void getTagSetReturnsAnEmptyTagSet() {
        final var underTest = EmptyPreparedSpan.newInstance(SpanOperationName.of(testName.getMethodName()));

        assertThat(underTest.getTagSet()).isEmpty();
    }

}