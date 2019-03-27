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
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.HashSet;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePermissions}.
 */
public final class ImmutablePermissionsTest {

    private Permissions underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutablePermissions.none();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePermissions.class, areImmutable());
    }

    @Ignore("EqualsVerifier does not like extending AbstractCollection")
    @Test
    public void testHashCodeAndEquals() {
        final Permissions red = Permissions.newInstance(TestConstants.Policy.PERMISSION_READ);
        final Permissions black = Permissions.newInstance(TestConstants.Policy.PERMISSION_WRITE);

        EqualsVerifier.forClass(ImmutablePermissions.class)
                .usingGetClass()
                .withPrefabValues(Permissions.class, red, black)
                .verify();
    }

    @Test
    public void createNewMutablePermissionsFromGivenSet() {
        final Collection<String> allMutablePermissions = new HashSet<>();
        allMutablePermissions.add(TestConstants.Policy.PERMISSION_READ);
        allMutablePermissions.add(TestConstants.Policy.PERMISSION_WRITE);
        underTest = ImmutablePermissions.of(allMutablePermissions);

        Assertions.assertThat(underTest).hasSameSizeAs(allMutablePermissions).containsAll(allMutablePermissions);
    }

    @Test
    public void containsWorksAsExpected() {
        underTest =
                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);

        assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ)).isTrue();
        assertThat(underTest.contains(TestConstants.Policy.PERMISSION_WRITE)).isTrue();

        assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ,
                TestConstants.Policy.PERMISSION_WRITE)).isTrue();

        underTest =
                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);

        assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ)).isTrue();
    }

    @Test
    public void createJsonRepresentationOfEmptyMutablePermissions() {
        final JsonValue actualJsonValue = underTest.toJson();

        assertThat(actualJsonValue).isEqualTo(JsonArray.newBuilder().build());
    }

}
