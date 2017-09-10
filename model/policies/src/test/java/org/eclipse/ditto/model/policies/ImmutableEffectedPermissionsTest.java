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

import static org.eclipse.ditto.model.policies.assertions.DittoPolicyAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEffectedPermissions}.
 */
public final class ImmutableEffectedPermissionsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableEffectedPermissions.class,
                areImmutable(),
                provided(Permissions.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEffectedPermissions.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final EffectedPermissions effectedPermissions = ImmutableEffectedPermissions.of(
                Arrays.asList(TestConstants.Policy.PERMISSION_READ, TestConstants.Policy.PERMISSION_WRITE),
                PoliciesModelFactory.noPermissions());

        final JsonObject effectedPermissionsJson = effectedPermissions.toJson();
        final EffectedPermissions effectedPermissions1 = ImmutableEffectedPermissions.fromJson(effectedPermissionsJson);

        assertThat(effectedPermissions).isEqualTo(effectedPermissions1);
    }

}
