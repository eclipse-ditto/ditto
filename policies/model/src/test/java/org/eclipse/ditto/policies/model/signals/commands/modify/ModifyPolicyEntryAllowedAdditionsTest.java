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
 * Unit test for {@link ModifyPolicyEntryAllowedAdditions}.
 */
public final class ModifyPolicyEntryAllowedAdditionsTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyEntryAllowedAdditions.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyEntryAllowedAdditions.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ModifyPolicyEntryAllowedAdditions.JSON_ALLOWED_ADDITIONS,
                    TestConstants.Policy.ALLOWED_ADDITIONS.stream()
                            .map(a -> JsonValue.of(a.getName()))
                            .collect(JsonCollectors.valuesToArray()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntryAllowedAdditions.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyEntryAllowedAdditions.of(null, TestConstants.Policy.LABEL,
                TestConstants.Policy.ALLOWED_ADDITIONS, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ModifyPolicyEntryAllowedAdditions.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.ALLOWED_ADDITIONS, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullAllowedAdditions() {
        ModifyPolicyEntryAllowedAdditions.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntryAllowedAdditions underTest =
                ModifyPolicyEntryAllowedAdditions.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.ALLOWED_ADDITIONS,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntryAllowedAdditions underTest =
                ModifyPolicyEntryAllowedAdditions.fromJson(KNOWN_JSON.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getAllowedAdditions()).isEqualTo(TestConstants.Policy.ALLOWED_ADDITIONS);
    }

    @Test
    public void fromJsonWithInvalidAllowedAdditionThrowsPolicyEntryInvalidException() {
        final JsonObject invalidJson = KNOWN_JSON.toBuilder()
                .set(ModifyPolicyEntryAllowedAdditions.JSON_ALLOWED_ADDITIONS,
                        JsonArray.of(JsonValue.of("subjects"), JsonValue.of("bogus")))
                .build();

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> ModifyPolicyEntryAllowedAdditions.fromJson(invalidJson,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }

    @Test
    public void setEntityWithInvalidAllowedAdditionThrowsPolicyEntryInvalidException() {
        final ModifyPolicyEntryAllowedAdditions underTest =
                ModifyPolicyEntryAllowedAdditions.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.LABEL, TestConstants.Policy.ALLOWED_ADDITIONS,
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonArray invalidArray = JsonArray.of(JsonValue.of("subjects"), JsonValue.of("bogus"));

        assertThatExceptionOfType(PolicyEntryInvalidException.class)
                .isThrownBy(() -> underTest.setEntity(invalidArray));
    }

}
