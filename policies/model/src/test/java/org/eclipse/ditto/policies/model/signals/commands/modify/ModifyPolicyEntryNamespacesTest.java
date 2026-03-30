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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.PolicyEntryInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyEntryNamespaces}.
 */
public final class ModifyPolicyEntryNamespacesTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyEntryNamespaces.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyEntryNamespaces.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ModifyPolicyEntryNamespaces.JSON_NAMESPACES,
                    TestConstants.Policy.NAMESPACES.stream()
                            .map(JsonValue::of)
                            .collect(JsonCollectors.valuesToArray()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntryNamespaces.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyEntryNamespaces.of(null, TestConstants.Policy.LABEL,
                TestConstants.Policy.NAMESPACES, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ModifyPolicyEntryNamespaces.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.NAMESPACES, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullNamespaces() {
        ModifyPolicyEntryNamespaces.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntryNamespaces underTest =
                ModifyPolicyEntryNamespaces.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.NAMESPACES,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntryNamespaces underTest =
                ModifyPolicyEntryNamespaces.fromJson(KNOWN_JSON.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getNamespaces()).isEqualTo(TestConstants.Policy.NAMESPACES);
    }

    @Test
    public void fromJsonWithInvalidNamespacePatternThrowsPolicyEntryInvalidException() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyPolicyEntryNamespaces.JSON_NAMESPACES,
                        JsonArray.of(JsonValue.of("com.acme"), JsonValue.of("*")))
                .build();

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> ModifyPolicyEntryNamespaces.fromJson(invalidJson,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }

    @Test
    public void setEntityWithInvalidNamespacePatternThrowsPolicyEntryInvalidException() {
        final ModifyPolicyEntryNamespaces underTest =
                ModifyPolicyEntryNamespaces.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.NAMESPACES,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonArray invalidArray = JsonArray.of(JsonValue.of("com.acme"), JsonValue.of("*"));

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> underTest.setEntity(invalidArray));
    }

    @Test
    public void fromJsonWithNonStringElementRejectsPayload() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyPolicyEntryNamespaces.JSON_NAMESPACES,
                        JsonArray.of(JsonValue.of(1), JsonValue.of("com.acme")))
                .build();

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> ModifyPolicyEntryNamespaces.fromJson(invalidJson,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }

    @Test
    public void setEntityWithNonStringElementRejectsPayload() {
        final ModifyPolicyEntryNamespaces underTest =
                ModifyPolicyEntryNamespaces.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.NAMESPACES,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonArray invalidArray = JsonArray.of(JsonValue.of(true), JsonValue.of("com.acme"));

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> underTest.setEntity(invalidArray));
    }

}
