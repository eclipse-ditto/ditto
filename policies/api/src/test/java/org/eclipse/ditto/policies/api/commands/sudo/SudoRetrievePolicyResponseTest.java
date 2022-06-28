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
package org.eclipse.ditto.policies.api.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.TestConstants;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrievePolicyResponse}.
 */
public final class SudoRetrievePolicyResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder() //
            .set(PolicySudoQueryCommandResponse.JsonFields.TYPE, SudoRetrievePolicyResponse.TYPE) //
            .set(PolicySudoQueryCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode()) //
            .set(PolicySudoQueryCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString()) //
            .set(SudoRetrievePolicyResponse.JSON_POLICY,
                    TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial())) //
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrievePolicyResponse.class, areImmutable(),
                provided(JsonObject.class, PolicyId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrievePolicyResponse.class) //
                .withRedefinedSuperclass() //
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicy() {
        SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, (Policy) null, EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonObject() {
        SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, (JsonObject) null, EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrievePolicyResponse underTest =
                SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.REGULAR.or(FieldType.SPECIAL));

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrievePolicyResponse underTest =
                SudoRetrievePolicyResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicy().toJson()).isEqualTo(TestConstants.Policy.POLICY.toJson());
    }

    @Test
    public void checkSudoCommandResponseRegistryWorks() {
        final SudoRetrievePolicyResponse sudoRetrieveThingResponse =
                SudoRetrievePolicyResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final CommandResponse commandResponse =
                GlobalCommandResponseRegistry.getInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingResponse).isEqualTo(commandResponse);
    }

}
