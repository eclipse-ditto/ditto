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
package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;

import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PermissionCheckWrapper}.
 */
public final class PermissionCheckWrapperTest {

    private static final ImmutablePermissionCheck PERMISSION_CHECK = ImmutablePermissionCheck.of(
            ResourceKey.newInstance("thing:/features/lamp/properties/on"),
            "org.eclipse.ditto:some-thing-1",
            List.of("READ")
            );
    private static final PolicyId POLICY_ID = PolicyId.of("policy:12345");

    @Test
    public void testConstructorAndFields() {
        PermissionCheckWrapper wrapper = new PermissionCheckWrapper(PERMISSION_CHECK, POLICY_ID);
        assertThat(PERMISSION_CHECK).isEqualTo(wrapper.permissionCheck());
        assertThat(POLICY_ID.toString()).isEqualTo(wrapper.policyId().toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        PermissionCheckWrapper wrapper1 = new PermissionCheckWrapper(PERMISSION_CHECK, POLICY_ID);
        PermissionCheckWrapper wrapper2 = new PermissionCheckWrapper(PERMISSION_CHECK, POLICY_ID);

        assertThat(wrapper1).isEqualTo(wrapper2);
        assertThat(wrapper1.hashCode()).isEqualTo(wrapper2.hashCode());
    }
}
