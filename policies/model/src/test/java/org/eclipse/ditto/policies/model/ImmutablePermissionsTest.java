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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
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

        DittoPolicyAssertions.assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ)).isTrue();
        DittoPolicyAssertions.assertThat(underTest.contains(TestConstants.Policy.PERMISSION_WRITE)).isTrue();

        DittoPolicyAssertions.assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ,
                TestConstants.Policy.PERMISSION_WRITE)).isTrue();

        underTest =
                Permissions.newInstance(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);

        DittoPolicyAssertions.assertThat(underTest.contains(TestConstants.Policy.PERMISSION_READ)).isTrue();
    }

    @Test
    public void equalPermissionsShareOneInstance() {
        final Permissions fromVarargs =
                ImmutablePermissions.of(TestConstants.Policy.PERMISSION_WRITE, TestConstants.Policy.PERMISSION_READ);
        final Permissions fromList = ImmutablePermissions.of(
                Arrays.asList(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE,
                        TestConstants.Policy.PERMISSION_READ));
        final Permissions fromLinkedHashSet = ImmutablePermissions.of(new LinkedHashSet<>(
                Arrays.asList(TestConstants.Policy.PERMISSION_WRITE, TestConstants.Policy.PERMISSION_READ)));

        Assertions.assertThat(fromList).isSameAs(fromVarargs).hasSize(2);
        Assertions.assertThat(fromLinkedHashSet).isSameAs(fromVarargs);
        Assertions.assertThat(ImmutablePermissions.of(fromVarargs)).isSameAs(fromVarargs);
    }

    @Test
    public void emptyPermissionsAreASingleton() {
        Assertions.assertThat(ImmutablePermissions.of(Collections.<String>emptyList())).isSameAs(ImmutablePermissions.none());
        Assertions.assertThat(ImmutablePermissions.none()).isEmpty();
    }

    @Test
    public void equalsAndHashCodeFollowTheSetContract() {
        final Permissions permissions =
                ImmutablePermissions.of(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE);
        final Set<String> hashSet =
                new HashSet<>(Arrays.asList(TestConstants.Policy.PERMISSION_WRITE, TestConstants.Policy.PERMISSION_READ));

        Assertions.assertThat(permissions).isEqualTo(hashSet);
        Assertions.assertThat(hashSet).isEqualTo(permissions);
        Assertions.assertThat(permissions.hashCode()).isEqualTo(hashSet.hashCode());
        Assertions.assertThat(permissions).isNotEqualTo(Collections.singleton(TestConstants.Policy.PERMISSION_READ));
    }

    @Test
    public void jsonRepresentationIsSortedAndDeterministic() {
        final Permissions permissions = ImmutablePermissions.of(TestConstants.Policy.PERMISSION_WRITE,
                "EXECUTE", TestConstants.Policy.PERMISSION_READ);

        DittoPolicyAssertions.assertThat(permissions.toJson())
                .isEqualTo(JsonArray.newBuilder().add("EXECUTE", "READ", "WRITE").build());
    }

    @Test(expected = NullPointerException.class)
    public void nullPermissionIsRejected() {
        ImmutablePermissions.of(TestConstants.Policy.PERMISSION_READ, (String) null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void iteratorDoesNotSupportRemove() {
        final Iterator<String> iterator = ImmutablePermissions.of(TestConstants.Policy.PERMISSION_READ).iterator();
        iterator.next();
        iterator.remove();
    }

    @Test
    public void createJsonRepresentationOfEmptyMutablePermissions() {
        final JsonValue actualJsonValue = underTest.toJson();

        DittoPolicyAssertions.assertThat(actualJsonValue).isEqualTo(JsonArray.newBuilder().build());
    }

}
