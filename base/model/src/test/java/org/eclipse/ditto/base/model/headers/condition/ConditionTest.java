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
package org.eclipse.ditto.base.model.headers.condition;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link org.eclipse.ditto.base.model.headers.condition.Condition}.
 */
public final class ConditionTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Condition.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(Condition.class, areImmutable());
    }

    @Test
    public void getCondition() {
        final String condition = "eq(attributes/value, 42)";
        final Condition underTest = Condition.of(condition);

        softly.assertThat(underTest.getRqlCondition()).isEqualTo(condition);
    }

    @Test
    public void testNullValueForCondition() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> Condition.of(null))
                .withFailMessage("The %s must not be null!", "condition")
                .withNoCause();
    }

    @Test
    public void testEmptyValueForCondition() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Condition.of(""))
                .withFailMessage("The %s must not be empty!", "condition")
                .withNoCause();
    }

}
