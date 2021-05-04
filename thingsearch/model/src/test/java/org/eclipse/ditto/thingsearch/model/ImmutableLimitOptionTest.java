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
package org.eclipse.ditto.thingsearch.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableLimitOption}.
 */
public final class ImmutableLimitOptionTest {

    private static final int KNOWN_OFFSET = 23;
    private static final int KNOWN_COUNT = 42;

    private ImmutableLimitOption underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableLimitOption.of(KNOWN_OFFSET, KNOWN_COUNT);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableLimitOption.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableLimitOption.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getOffsetReturnsExpected() {
        assertThat(underTest.getOffset()).isEqualTo(KNOWN_OFFSET);
    }

    @Test
    public void getCountReturnsExpected() {
        assertThat(underTest.getCount()).isEqualTo(KNOWN_COUNT);
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "limit(23,42)";

        assertThat(underTest.toString()).isEqualTo(expected);
    }

}
