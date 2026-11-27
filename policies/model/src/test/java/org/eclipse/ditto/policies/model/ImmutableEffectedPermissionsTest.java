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

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.assertions.DittoPolicyAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEffectedPermissions}.
 */
public final class ImmutableEffectedPermissionsTest {

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

        DittoPolicyAssertions.assertThat(effectedPermissions).isEqualTo(effectedPermissions1);
    }

    @Test
    public void equalEffectedPermissionsShareInstancesAndTheirPermissions() {
        final EffectedPermissions first = ImmutableEffectedPermissions.of(
                Arrays.asList(TestConstants.Policy.PERMISSION_WRITE, TestConstants.Policy.PERMISSION_READ),
                PoliciesModelFactory.noPermissions());
        final EffectedPermissions second = ImmutableEffectedPermissions.fromJson(first.toJson());

        Assertions.assertThat(second).isSameAs(first);
        Assertions.assertThat(second.getGrantedPermissions()).isSameAs(first.getGrantedPermissions());
        Assertions.assertThat(first.getRevokedPermissions()).isSameAs(PoliciesModelFactory.noPermissions());
    }

}
