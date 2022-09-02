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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonPointer;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableResourceKey}.
 */
public final class ImmutableResourceKeyTest {

    private ImmutableResourceKey underTest;

    @Before
    public void setUp() {
        underTest = ImmutableResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE,
                TestConstants.Policy.RESOURCE_PATH);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableResourceKey.class,
                areImmutable(),
                provided(JsonPointer.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResourceKey.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getResourceTypeReturnsExpected() {
        assertThat(underTest.getResourceType()).isEqualTo(TestConstants.Policy.RESOURCE_TYPE);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        assertThat((CharSequence) underTest.getResourcePath()).isEqualTo(TestConstants.Policy.RESOURCE_PATH);
    }

    @Test
    public void testToStringWorksAsExpected() {
        final String expected = "thing:/foo/bar";
        final ResourceKey underTest = ImmutableResourceKey.newInstance("thing", JsonPointer.of("/foo/bar"));

        assertThat(underTest.toString()).hasToString(expected);
    }

}
