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
package org.eclipse.ditto.policies.model.signals.commands.query;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrievePolicyImportEntryAdditionResponse}.
 */
public final class RetrievePolicyImportEntryAdditionResponseTest {

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, RetrievePolicyImportEntryAdditionResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrievePolicyImportEntryAdditionResponse.JSON_IMPORTED_POLICY_ID,
                    TestConstants.Policy.IMPORTED_POLICY_ID.toString())
            .set(RetrievePolicyImportEntryAdditionResponse.JSON_LABEL,
                    TestConstants.Policy.LABEL.toString())
            .set(RetrievePolicyImportEntryAdditionResponse.JSON_ENTRY_ADDITION,
                    TestConstants.Policy.ENTRY_ADDITION.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicyImportEntryAdditionResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicyImportEntryAdditionResponse underTest =
                RetrievePolicyImportEntryAdditionResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.IMPORTED_POLICY_ID,
                        TestConstants.Policy.LABEL,
                        TestConstants.Policy.ENTRY_ADDITION.toJson(FieldType.regularOrSpecial()),
                        TestConstants.EMPTY_DITTO_HEADERS);

        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicyImportEntryAdditionResponse underTest =
                RetrievePolicyImportEntryAdditionResponse.fromJson(KNOWN_JSON,
                        TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getEntryAddition()).isEqualTo(
                TestConstants.Policy.ENTRY_ADDITION.toJson(FieldType.regularOrSpecial()));
    }

}
