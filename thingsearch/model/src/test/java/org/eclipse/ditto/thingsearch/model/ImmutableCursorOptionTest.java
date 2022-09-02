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
 * Unit test for {@link ImmutableCursorOption}.
 */
public final class ImmutableCursorOptionTest {

    private static final String CURSOR = "CURSOR";

    private ImmutableCursorOption underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableCursorOption.of(CURSOR);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableCursorOption.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableCursorOption.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getCursorReturnsExpected() {
        assertThat(underTest.getCursor()).isEqualTo(CURSOR);
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "cursor(CURSOR)";

        assertThat(underTest.toString()).hasToString(expected);
    }

}
