/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

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

        assertThat(underTest.toString()).isEqualTo(expected);
    }

}
