/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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


import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableResourcePermissions}.
 */
public final class ImmutableResourcePermissionsTest {

    private static final ResourceKey TEST_RESOURCE_KEY = ResourceKey.newInstance("thing:/features/lamp/properties/on");
    private static final List<String> TEST_PERMISSIONS = Arrays.asList("READ", "WRITE");

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set("resourceKey", TEST_RESOURCE_KEY.toString())
            .set("hasPermissions", JsonArray.of(TEST_PERMISSIONS))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResourcePermissions.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void createInstanceWithValidArguments() {
        final ImmutableResourcePermissions underTest = ImmutableResourcePermissions.newInstance(TEST_RESOURCE_KEY, TEST_PERMISSIONS);

        assertThat(underTest.getResourceKey()).isEqualTo(TEST_RESOURCE_KEY);
        assertThat(underTest.getPermissions()).containsExactlyElementsOf(TEST_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullResourceKey() {
        ImmutableResourcePermissions.newInstance(null, TEST_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPermissions() {
        ImmutableResourcePermissions.newInstance(TEST_RESOURCE_KEY, null);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ImmutableResourcePermissions underTest = ImmutableResourcePermissions.newInstance(TEST_RESOURCE_KEY, TEST_PERMISSIONS);
        final JsonObject actualJson = underTest.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ImmutableResourcePermissions underTest = ImmutableResourcePermissions.fromJson(KNOWN_JSON);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getResourceKey()).isEqualTo(TEST_RESOURCE_KEY);
        assertThat(underTest.getPermissions()).containsExactlyElementsOf(TEST_PERMISSIONS);
    }


    @Test
    public void testToString() {
        final ImmutableResourcePermissions underTest = ImmutableResourcePermissions.newInstance(TEST_RESOURCE_KEY, TEST_PERMISSIONS);
        final String expectedString = "ResourcePermissions [resourceKey=" + TEST_RESOURCE_KEY + ", permissions=" + TEST_PERMISSIONS + "]";

        assertThat(underTest.toString()).isEqualTo(expectedString);
    }
}