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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ShardNumberCalculator}.
 */
public final class ShardNumberCalculatorTest {

    private static final byte NUMBER_OF_SHARDS = 10;

    private ShardNumberCalculator underTest;

    @Before
    public void setUp() {
        underTest = ShardNumberCalculator.newInstance(NUMBER_OF_SHARDS);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ShardNumberCalculator.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ShardNumberCalculator.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithZeroNumberOfShards() {
        final var numberOfShards = 0;

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ShardNumberCalculator.newInstance(numberOfShards))
                .withMessage("The number of shards <%d> is less than one.", numberOfShards)
                .withNoCause();
    }

    @Test
    public void calculateShardNumberForNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.calculateShardNumber(null))
                .withMessage("The entityId must not be null!")
                .withNoCause();
    }

    @Test
    public void calculateShardNumberForEntityIdWhoseHashCodeIsIntegerMinValue() {
        try (final var softly = new AutoCloseableSoftAssertions()) {
            final var stringWithMinHashCode = "polygenelubricants";

            softly.assertThat(stringWithMinHashCode.hashCode()).isEqualTo(Integer.MIN_VALUE);
            softly.assertThat(underTest.calculateShardNumber(stringWithMinHashCode)).isEqualTo(0);
        }
    }

    @Test
    public void calculateShardNumberReturnsExpected() {
        try (final var softly = new AutoCloseableSoftAssertions()) {
            final var entityId = "Plumbus";

            softly.assertThat(entityId.hashCode()).isEqualTo(1189195852);
            softly.assertThat(underTest.calculateShardNumber(entityId)).isEqualTo(2);
        }
    }

}
