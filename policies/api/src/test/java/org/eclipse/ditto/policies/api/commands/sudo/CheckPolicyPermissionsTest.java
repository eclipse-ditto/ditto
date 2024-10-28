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
package org.eclipse.ditto.policies.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.Arrays;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.ResourcePermissionFactory;
import org.eclipse.ditto.policies.model.ResourcePermissions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link CheckPolicyPermissions}.
 */
public final class CheckPolicyPermissionsTest {

    private static final PolicyId TEST_POLICY_ID = PolicyId.of("org.eclipse.ditto:some-policy-1");
    private static final Map<String, ResourcePermissions> TEST_PERMISSIONS_MAP = Map.of(
            "lamp_reader",
            ResourcePermissionFactory.newInstance(
                    ResourceKey.newInstance("thing:/features/lamp/properties/on"),
                    Arrays.asList("READ", "WRITE"))
    );

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Command.JsonFields.TYPE, CheckPolicyPermissions.TYPE)
            .set("policyId", TEST_POLICY_ID.toString())
            .set("permissionsMap", JsonFactory.newObjectBuilder()
                    .set("lamp_reader", JsonFactory.newObjectBuilder()
                            .set("resourceKey", "thing:/features/lamp/properties/on")
                            .set("hasPermissions", JsonFactory.newArray().add("READ").add("WRITE"))
                            .build())
                    .build())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CheckPolicyPermissions.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final CheckPolicyPermissions underTest = CheckPolicyPermissions.of(TEST_POLICY_ID, TEST_PERMISSIONS_MAP, EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.REGULAR.or(FieldType.SPECIAL));

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final CheckPolicyPermissions underTest = CheckPolicyPermissions.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntityId().toString()).isEqualTo(TEST_POLICY_ID.toString());
        assertThat(underTest.getPermissionsMap()).containsKey("lamp_reader");
    }

    @Test
    public void testToString() {
        final CheckPolicyPermissions underTest = CheckPolicyPermissions.of(TEST_POLICY_ID, TEST_PERMISSIONS_MAP, EMPTY_DITTO_HEADERS);
        final String expectedString = "CheckPolicyPermissions [type=policies.sudo.commands:checkPolicyPermissions, dittoHeaders="
                + EMPTY_DITTO_HEADERS + ", policyId=" + TEST_POLICY_ID + ", permissionsMap=" + TEST_PERMISSIONS_MAP + "]";

        assertThat(underTest.toString()).isEqualTo(expectedString);
    }
}
