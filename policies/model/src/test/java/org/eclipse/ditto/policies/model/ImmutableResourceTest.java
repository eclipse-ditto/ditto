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

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableResource}.
 */
public final class ImmutableResourceTest {

    private static final EffectedPermissions EFFECTED_PERMISSIONS =
            EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL, TestConstants.Policy.PERMISSIONS_ALL);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableResource.class,
                areImmutable(),
                provided(ResourceKey.class, JsonPointer.class, EffectedPermissions.class, JsonFieldDefinition.class)
                        .areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableResource.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Resource resource1 = ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY,
                EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL,
                        PoliciesModelFactory.noPermissions()));

        final JsonObject resourceJson = resource1.toJson();
        final Resource resource2 = ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, resourceJson);

        assertThat(resource1).isEqualTo(resource2);
    }

    @Test
    public void createResourceSuccess() {
        final Resource underTest = ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, EFFECTED_PERMISSIONS);

        assertThat(underTest).isNotNull();
        assertThat((Object) underTest.getPath()).isEqualTo(TestConstants.Policy.RESOURCE_PATH);
        assertThat(underTest.getEffectedPermissions()).isEqualTo(EFFECTED_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void createResourceWithJsonValueNull() {
        ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, (JsonValue) null);
    }

    @Test(expected = NullPointerException.class)
    public void createResourceWithEffectedPermissionsNull() {
        ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, (EffectedPermissions) null);
    }

    @Test(expected = DittoJsonException.class)
    public void createResourceWithJsonValueInvalidType() {
        ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, JsonFactory.newValue(true));
    }

    @Test(expected = DittoJsonException.class)
    public void createResourceWithJsonValueMissingFields() {
        ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, JsonObject.empty());
    }

    @Test
    public void createResourceWithJsonValueSuccess() {
        final Resource underTest =
                ImmutableResource.of(TestConstants.Policy.RESOURCE_KEY, EFFECTED_PERMISSIONS.toJson());

        assertThat(underTest).isNotNull();
        assertThat((Object) underTest.getPath()).isEqualTo(TestConstants.Policy.RESOURCE_PATH);
        assertThat(underTest.getEffectedPermissions()).isEqualTo(EFFECTED_PERMISSIONS);
    }

}
