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
import static org.eclipse.ditto.model.policies.TestConstants.Policy.RESOURCE_KEY;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
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
        final Resource resource1 = ImmutableResource.of(RESOURCE_KEY,
                EffectedPermissions.newInstance(TestConstants.Policy.PERMISSIONS_ALL,
                        PoliciesModelFactory.noPermissions()));

        final JsonObject resourceJson = resource1.toJson();
        final Resource resource2 = ImmutableResource.of(RESOURCE_KEY, resourceJson);

        assertThat(resource1).isEqualTo(resource2);
    }

    @Test
    public void createResourceSuccess() {
        final Resource underTest = ImmutableResource.of(RESOURCE_KEY, EFFECTED_PERMISSIONS);

        assertThat(underTest).isNotNull();
        assertThat((Object) underTest.getPath()).isEqualTo(TestConstants.Policy.RESOURCE_PATH);
        assertThat(underTest.getEffectedPermissions()).isEqualTo(EFFECTED_PERMISSIONS);
    }

    @Test(expected = NullPointerException.class)
    public void createResourceWithJsonValueNull() {
        ImmutableResource.of(RESOURCE_KEY, (JsonValue) null);
    }

    @Test(expected = NullPointerException.class)
    public void createResourceWithEffectedPermissionsNull() {
        ImmutableResource.of(RESOURCE_KEY, (EffectedPermissions) null);
    }

    @Test(expected = DittoJsonException.class)
    public void createResourceWithJsonValueInvalidType() {
        ImmutableResource.of(RESOURCE_KEY, JsonFactory.newValue(true));
    }

    @Test(expected = DittoJsonException.class)
    public void createResourceWithJsonValueMissingFields() {
        ImmutableResource.of(RESOURCE_KEY,
                JsonFactory.newObjectBuilder()
                        .set(Resource.JsonFields.SCHEMA_VERSION, JsonSchemaVersion.V_2.toInt())
                        .build());
    }

    @Test
    public void createResourceWithJsonValueSuccess() {
        final Resource underTest =
                ImmutableResource.of(RESOURCE_KEY, EFFECTED_PERMISSIONS.toJson());

        assertThat(underTest).isNotNull();
        assertThat((Object) underTest.getPath()).isEqualTo(TestConstants.Policy.RESOURCE_PATH);
        assertThat(underTest.getEffectedPermissions()).isEqualTo(EFFECTED_PERMISSIONS);
    }

}
