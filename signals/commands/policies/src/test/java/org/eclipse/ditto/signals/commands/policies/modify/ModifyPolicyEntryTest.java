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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.PolicyEntry;
import org.eclipse.ditto.model.policies.id.PolicyId;
import org.eclipse.ditto.model.policies.id.PolicyIdInvalidException;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyEntry}.
 */
public class ModifyPolicyEntryTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyEntry.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyEntry.JSON_LABEL, TestConstants.Policy.POLICY_ENTRY.getLabel().toString())
            .set(ModifyPolicyEntry.JSON_POLICY_ENTRY,
                    TestConstants.Policy.POLICY_ENTRY.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyEntry.class,
                areImmutable(),
                provided(PolicyEntry.class, PolicyId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyEntry.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyEntry.of((PolicyId) null, TestConstants.Policy.POLICY_ENTRY,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = PolicyIdInvalidException.class)
    public void tryToCreateInstanceWithNullPolicyIdString() {
        ModifyPolicyEntry.of((String) null, TestConstants.Policy.POLICY_ENTRY,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void tryToCreateInstanceWithInvalidPolicyId() {
        assertThatExceptionOfType(PolicyIdInvalidException.class)
                .isThrownBy(() -> ModifyPolicyEntry.of("undefined", TestConstants.Policy.POLICY_ENTRY,
                        TestConstants.EMPTY_DITTO_HEADERS));
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyEntry() {
        ModifyPolicyEntry.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyEntry underTest = ModifyPolicyEntry.of(
                TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_ENTRY, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyEntry underTest =
                ModifyPolicyEntry.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicyEntry()).isEqualTo(TestConstants.Policy.POLICY_ENTRY);
    }

}
