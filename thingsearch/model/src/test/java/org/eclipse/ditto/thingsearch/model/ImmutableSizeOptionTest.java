/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
 * Unit test for {@link ImmutableSizeOption}.
 */
public final class ImmutableSizeOptionTest {

    private static final int SIZE = 14;

    private ImmutableSizeOption underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableSizeOption.of(SIZE);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSizeOption.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSizeOption.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getSizeReturnsExpected() {
        assertThat(underTest.getSize()).isEqualTo(SIZE);
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "size(14)";

        assertThat(underTest.toString()).hasToString(expected);
    }

}
