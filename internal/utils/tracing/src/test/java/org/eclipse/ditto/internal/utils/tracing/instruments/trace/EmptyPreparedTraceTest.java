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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EmptyPreparedTrace}.
 */
public final class EmptyPreparedTraceTest {

    @Rule
    public final TestName testName = new TestName();

    @Test
    public void assertImmutability() {
        assertInstancesOf(EmptyPreparedTrace.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EmptyPreparedTrace.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullOperationNameThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> EmptyPreparedTrace.newInstance(null))
                .withMessage("The operationName must not be null!")
                .withNoCause();
    }

    @Test
    public void operationNameIsPropagatedToStartedTrace() {
        final var operationName = TraceOperationName.of(testName.getMethodName());
        final var underTest = EmptyPreparedTrace.newInstance(operationName);

        final var startedTrace = underTest.start();

        assertThat((CharSequence) startedTrace.getOperationName()).isEqualTo(operationName);
    }

}