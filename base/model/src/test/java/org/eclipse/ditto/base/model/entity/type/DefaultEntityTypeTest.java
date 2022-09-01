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
package org.eclipse.ditto.base.model.entity.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.base.model.entity.type.DefaultEntityType}.
 */
public final class DefaultEntityTypeTest {

    private static final String KNOWN_VALUE = "shub-niggurath";

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEntityType.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultEntityType.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultEntityType.of(null))
                .withMessage("The value must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithEmptyValue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DefaultEntityType.of(""))
                .withMessage("The argument 'value' must not be empty!")
                .withNoCause();
    }

    @Test
    public void toStringReturnsExpected() {
        final DefaultEntityType underTest = DefaultEntityType.of(KNOWN_VALUE);

        assertThat(underTest.toString()).hasToString(KNOWN_VALUE);
    }

    @Test
    public void compareToWorksAsExpected() {
        final DefaultEntityType entityTypeA = DefaultEntityType.of("a");
        final DefaultEntityType entityTypeB = DefaultEntityType.of("b");

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(entityTypeA.compareTo(entityTypeA))
                    .as("compare with equal")
                    .isZero();
            softly.assertThat(entityTypeA.compareTo(entityTypeB))
                    .as("compare with greater")
                    .isNegative();
            softly.assertThat(entityTypeB.compareTo(entityTypeA))
                    .as("compare with less")
                    .isPositive();
        }
    }

}
