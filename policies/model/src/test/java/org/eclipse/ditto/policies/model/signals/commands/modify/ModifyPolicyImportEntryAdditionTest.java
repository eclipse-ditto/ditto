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

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyImportEntryAddition}.
 */
public final class ModifyPolicyImportEntryAdditionTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ModifyPolicyImportEntryAddition.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyImportEntryAddition.JSON_IMPORTED_POLICY_ID,
                    TestConstants.Policy.IMPORTED_POLICY_ID.toString())
            .set(ModifyPolicyImportEntryAddition.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ModifyPolicyImportEntryAddition.JSON_ENTRY_ADDITION,
                    TestConstants.Policy.ENTRY_ADDITION.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyImportEntryAddition.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ModifyPolicyImportEntryAddition.of(null, TestConstants.Policy.IMPORTED_POLICY_ID,
                TestConstants.Policy.ENTRY_ADDITION, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullImportedPolicyId() {
        ModifyPolicyImportEntryAddition.of(TestConstants.Policy.POLICY_ID, null,
                TestConstants.Policy.ENTRY_ADDITION, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullEntryAddition() {
        ModifyPolicyImportEntryAddition.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.IMPORTED_POLICY_ID, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyImportEntryAddition underTest =
                ModifyPolicyImportEntryAddition.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.IMPORTED_POLICY_ID, TestConstants.Policy.ENTRY_ADDITION,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyImportEntryAddition underTest =
                ModifyPolicyImportEntryAddition.fromJson(KNOWN_JSON.toString(),
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntryAddition()).isEqualTo(TestConstants.Policy.ENTRY_ADDITION);
    }

}
