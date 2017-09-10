/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.models.policies.commands.sudo;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.services.models.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoRetrievePolicyResponse}.
 */
public final class SudoRetrievePolicyResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder() //
            .set(SudoCommandResponse.JsonFields.TYPE, SudoRetrievePolicyResponse.TYPE) //
            .set(SudoCommandResponse.JsonFields.STATUS, HttpStatusCode.OK.toInt()) //
            .set(SudoCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID) //
            .set(SudoRetrievePolicyResponse.JSON_POLICY,
                    TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial())) //
            .build();

    private static final DittoHeaders EMPTY_DITTO_HEADERS = DittoHeaders.empty();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrievePolicyResponse.class, areImmutable(),
                provided(JsonObject.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoRetrievePolicyResponse.class) //
                .withRedefinedSuperclass() //
                .verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicy() {
        SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, (Policy) null, EMPTY_DITTO_HEADERS);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonObject() {
        SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, (JsonObject) null, EMPTY_DITTO_HEADERS);
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final SudoRetrievePolicyResponse underTest =
                SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.REGULAR.or(FieldType.SPECIAL));

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final SudoRetrievePolicyResponse underTest =
                SudoRetrievePolicyResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicy().toJson()).isEqualTo(TestConstants.Policy.POLICY.toJson());
    }

    /** */
    @Test
    public void checkSudoCommandResponseRegistryWorks() {
        final SudoRetrievePolicyResponse sudoRetrieveThingResponse =
                SudoRetrievePolicyResponse.fromJson(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        final SudoCommandResponse sudoCommandResponse =
                SudoCommandResponseRegistry.newInstance().parse(KNOWN_JSON.toString(), EMPTY_DITTO_HEADERS);

        assertThat(sudoRetrieveThingResponse).isEqualTo(sudoCommandResponse);
    }

}
