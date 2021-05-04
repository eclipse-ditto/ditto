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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableResources}.
 */
public final class ImmutableResourcesTest {

    private static final JsonPointer KNOWN_RESOURCE_PATH_0 = JsonPointer.empty();
    private static final JsonPointer KNOWN_RESOURCE_PATH_1 = JsonPointer.of("/foo/bar");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableResources.class,
                areImmutable(),
                assumingFields("resources").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResources.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Collection<Resource> resourceList = new ArrayList<>();
        resourceList.add(Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, KNOWN_RESOURCE_PATH_0),
                EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL, PoliciesModelFactory.newPermissions("FOO"))));
        resourceList.add(Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, KNOWN_RESOURCE_PATH_1),
                EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL, PoliciesModelFactory.noPermissions())));
        resourceList.add(Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, "two/muh/bla"),
                EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                        PoliciesModelFactory.newPermissions(TestConstants.Policy.PERMISSION_READ))));

        final ImmutableResources resources = ImmutableResources.of(resourceList);

        final JsonObject resourcesJson = resources.toJson();
        final Resources resourcesFromJson = ImmutableResources.fromJson(resourcesJson);

        assertThat(resources).isEqualTo(resourcesFromJson);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createResourcesWithSamePathsShouldFail() {
        final Collection<Resource> resourceList = new ArrayList<>();
        resourceList.add(Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, KNOWN_RESOURCE_PATH_1),
                EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL, PoliciesModelFactory.noPermissions())));
        resourceList.add(Resource.newInstance(ResourceKey.newInstance(TestConstants.Policy.RESOURCE_TYPE, KNOWN_RESOURCE_PATH_1),
                EffectedPermissions.newInstance(PoliciesModelFactory.noPermissions(),
                        PoliciesModelFactory.newPermissions(TestConstants.Policy.PERMISSION_READ))));

        ImmutableResources.of(resourceList);
    }

}
