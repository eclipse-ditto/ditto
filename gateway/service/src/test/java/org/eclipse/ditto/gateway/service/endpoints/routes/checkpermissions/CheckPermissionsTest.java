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

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link CheckPermissions}.
 */
public final class CheckPermissionsTest {

    @Test
    public void fromJsonToJson() {
        final DittoHeaders headers = DittoHeaders.newBuilder()
                .correlationId("test-correlation-id")
                .build();
        var permissionResource =
                ImmutablePermissionCheck.of(ResourceKey.newInstance("thing:/features/lamp/properties/on"),
                        "org.eclipse.ditto:some-thing-1",
                        List.of("WRITE"));
        Map<String, ImmutablePermissionCheck> permissionChecks = new LinkedHashMap<>();
        permissionChecks.put("check1", permissionResource);
        final CheckPermissions command = CheckPermissions.of(permissionChecks, headers);

        final CheckPermissions deserialized =
                CheckPermissions.fromJson(JsonObject.newBuilder().set("check1", permissionResource.toJson()).build(),
                        headers);

        assertThat(deserialized).isEqualTo(command);
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(CheckPermissions.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testEmptyPermissionChecks() {
        final DittoHeaders headers = DittoHeaders.newBuilder().build();
        final Map<String, ImmutablePermissionCheck> emptyPermissionChecks = new LinkedHashMap<>();

        final CheckPermissions command = CheckPermissions.of(emptyPermissionChecks, headers);

        assertThat(command.getPermissionChecks()).isEmpty();
        assertThat(command.getDittoHeaders()).isEqualTo(headers);
    }

}
