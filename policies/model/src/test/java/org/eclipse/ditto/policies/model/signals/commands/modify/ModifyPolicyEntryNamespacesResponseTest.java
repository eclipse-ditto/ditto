/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyEntryNamespacesResponse}.
 */
public final class ModifyPolicyEntryNamespacesResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyEntryNamespacesResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyEntryNamespacesResponse.JSON_LABEL,
                    TestConstants.Policy.LABEL.toString())
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntryNamespacesResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntryNamespacesResponse underTest =
                ModifyPolicyEntryNamespacesResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.EMPTY_DITTO_HEADERS);

        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntryNamespacesResponse underTest =
                ModifyPolicyEntryNamespacesResponse.fromJson(KNOWN_JSON,
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final ModifyPolicyEntryNamespacesResponse underTest =
                ModifyPolicyEntryNamespacesResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest.getResourcePath())
                .isEqualTo(JsonPointer.of("/entries/" + TestConstants.Policy.LABEL + "/namespaces"));
    }

}
