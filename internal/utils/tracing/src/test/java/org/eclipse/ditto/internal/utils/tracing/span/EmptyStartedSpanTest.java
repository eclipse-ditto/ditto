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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EmptyStartedSpan}.
 */
public final class EmptyStartedSpanTest {

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void assertImmutability() {
        assertInstancesOf(EmptyStartedSpan.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EmptyStartedSpan.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullOperationNameThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> EmptyStartedSpan.newInstance(null))
                .withMessage("The operationName must not be null!")
                .withNoCause();
    }

    @Test
    public void getOperationNameReturnsExpected() {
        final var operationName = SpanOperationName.of(testName.getMethodName());
        final var underTest = EmptyStartedSpan.newInstance(operationName);

        assertThat((CharSequence) underTest.getOperationName()).isEqualTo(operationName);
    }

}