/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePolicyRevision}.
 */
public final class ImmutablePolicyRevisionTest {

    private static final long LOWER_VALUE = 5;
    private static final long DEFAULT_VALUE = 23;
    private static final long GREATER_VALUE = 42;

    private ImmutablePolicyRevision underTest;

    @Before
    public void setUp() {
        underTest = ImmutablePolicyRevision.of(DEFAULT_VALUE);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePolicyRevision.class,
                areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePolicyRevision.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toLongReturnsExpected() {
        assertThat(underTest.toLong()).isEqualTo(DEFAULT_VALUE);
    }

    @Test
    public void toStringReturnsExpected() {
        assertThat(underTest.toString()).hasToString(String.valueOf(DEFAULT_VALUE));
    }

    @Test
    public void isGreaterThanWorksAsExpected() {
        final ImmutablePolicyRevision lessOther = ImmutablePolicyRevision.of(LOWER_VALUE);
        final ImmutablePolicyRevision greaterOther = ImmutablePolicyRevision.of(GREATER_VALUE);

        assertThat(underTest.isGreaterThan(lessOther))
                .as("%s is greater than %s", underTest, lessOther)
                .isTrue();
        assertThat(underTest.isGreaterThan(underTest))
                .as("%s is not greater than %s", underTest, underTest)
                .isFalse();
        assertThat(underTest.isGreaterThan(greaterOther))
                .as("%s is not greater than %s", underTest, greaterOther)
                .isFalse();
    }

    @Test
    public void isGreaterThanOrEqualToWorksAsExpected() {
        final ImmutablePolicyRevision lessOther = ImmutablePolicyRevision.of(LOWER_VALUE);
        final ImmutablePolicyRevision greaterOther = ImmutablePolicyRevision.of(GREATER_VALUE);

        assertThat(underTest.isGreaterThanOrEqualTo(lessOther))
                .as("%s is greater than %s", underTest, lessOther)
                .isTrue();
        assertThat(underTest.isGreaterThanOrEqualTo(underTest))
                .as("%s is equal to than %s", underTest, underTest)
                .isTrue();
        assertThat(underTest.isGreaterThanOrEqualTo(greaterOther))
                .as("%s is not greater than %s", underTest, greaterOther)
                .isFalse();
    }

    @Test
    public void isLowerThanWorksAsExpected() {
        final ImmutablePolicyRevision lessOther = ImmutablePolicyRevision.of(LOWER_VALUE);
        final ImmutablePolicyRevision greaterOther = ImmutablePolicyRevision.of(GREATER_VALUE);

        assertThat(underTest.isLowerThan(lessOther))
                .as("%s is not lower than %s", underTest, lessOther)
                .isFalse();
        assertThat(underTest.isLowerThan(underTest))
                .as("%s is not lower than %s", underTest, underTest)
                .isFalse();
        assertThat(underTest.isLowerThan(greaterOther))
                .as("%s is lower than %s", underTest, greaterOther)
                .isTrue();
    }

    @Test
    public void isLowerThanOrEqualToWorksAsExpected() {
        final ImmutablePolicyRevision lessOther = ImmutablePolicyRevision.of(LOWER_VALUE);
        final ImmutablePolicyRevision greaterOther = ImmutablePolicyRevision.of(GREATER_VALUE);

        assertThat(underTest.isLowerThanOrEqualTo(lessOther))
                .as("%s is not lower than %s", underTest, lessOther)
                .isFalse();
        assertThat(underTest.isLowerThanOrEqualTo(underTest))
                .as("%s is equal to %s", underTest, underTest)
                .isTrue();
        assertThat(underTest.isLowerThanOrEqualTo(greaterOther))
                .as("%s is lower than %s", underTest, greaterOther)
                .isTrue();
    }

}
