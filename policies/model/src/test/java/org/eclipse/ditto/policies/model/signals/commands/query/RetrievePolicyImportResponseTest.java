/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrievePolicyImportResponse}.
 */
public final class RetrievePolicyImportResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, RetrievePolicyImportResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(RetrievePolicyImportResponse.JSON_IMPORTED_POLICY_ID, TestConstants.Policy.POLICY_IMPORT.getImportedPolicyId().toString())
            .set(RetrievePolicyImportResponse.JSON_POLICY_IMPORT, TestConstants.Policy.POLICY_IMPORT.toJson())
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();


    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrievePolicyImportResponse.class, areImmutable(),
                provided(JsonObject.class, PolicyId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrievePolicyImportResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyImport() {
        RetrievePolicyImportResponse.of(TestConstants.Policy.POLICY_ID, null, EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonObject() {
        RetrievePolicyImportResponse.of(TestConstants.Policy.POLICY_ID, PolicyId.of("foo:bar"), null, EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        RetrievePolicyImportResponse.of(TestConstants.Policy.POLICY_ID, null, JsonFactory.newObjectBuilder().build(),
                EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final RetrievePolicyImportResponse underTest =
                RetrievePolicyImportResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY_IMPORT,
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final RetrievePolicyImportResponse underTest =
                RetrievePolicyImportResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicyImport()).isEqualTo(TestConstants.Policy.POLICY_IMPORT);
    }
}
