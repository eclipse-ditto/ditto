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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.things.model.assertions.DittoThingsAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableThingRevision}.
 */
public final class ImmutableThingRevisionTest {

    private static final long DEFAULT_LONG_VALUE = 42L;
    private static final long LOWER_LONG_VALUE = 23L;
    private static final long GREATER_LONG_VALUE = 1337L;

    private ThingRevision underTest = null;


    @Before
    public void setUp() {
        underTest = ImmutableThingRevision.of(DEFAULT_LONG_VALUE);
    }


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableThingRevision.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableThingRevision.class)
                .usingGetClass()
                .verify();
    }


    @Test
    public void toLongReturnsExpected() {
        assertThat(underTest.toLong()).isEqualTo(DEFAULT_LONG_VALUE);
    }


    @Test
    public void toStringReturnsExpected() {
        final String expected = String.valueOf(DEFAULT_LONG_VALUE);

        assertThat(underTest.toString()).isEqualTo(expected);
    }


    @Test
    public void revisionIsLowerThanOther() {
        final ThingRevision other = ImmutableThingRevision.of(GREATER_LONG_VALUE);

        assertThat(underTest.isLowerThan(other)).isTrue();
    }


    @Test
    public void revisionIsGreaterThanOther() {
        final ThingRevision other = ImmutableThingRevision.of(LOWER_LONG_VALUE);

        assertThat(underTest.isGreaterThan(other)).isTrue();
    }


    @Test
    public void revisionIsLowerThanOrEqualToOther() {
        assertThat(underTest.isLowerThanOrEqualTo(ImmutableThingRevision.of(DEFAULT_LONG_VALUE))).isTrue();
        assertThat(underTest.isLowerThanOrEqualTo(ImmutableThingRevision.of(GREATER_LONG_VALUE))).isTrue();
    }


    @Test
    public void revisionIsGreaterThanOrEqualToOther() {
        assertThat(underTest.isGreaterThanOrEqualTo(ImmutableThingRevision.of(DEFAULT_LONG_VALUE))).isTrue();
        assertThat(underTest.isGreaterThanOrEqualTo(ImmutableThingRevision.of(LOWER_LONG_VALUE))).isTrue();
    }

}
