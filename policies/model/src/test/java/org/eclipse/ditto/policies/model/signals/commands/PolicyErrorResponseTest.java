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
package org.eclipse.ditto.policies.model.signals.commands;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse}.
 */
public class PolicyErrorResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, PolicyErrorResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NOT_FOUND.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(PolicyCommandResponse.JsonFields.PAYLOAD,
                    TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(PolicyErrorResponse.class,
                areImmutable(),
                provided(DittoRuntimeException.class, PolicyId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(PolicyErrorResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final PolicyErrorResponse underTest =
                PolicyErrorResponse.of(TestConstants.Policy.POLICY_ID,
                        TestConstants.Policy.POLICY_NOT_ACCESSIBLE_EXCEPTION,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final PolicyErrorResponse underTest =
                PolicyErrorResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }

    @Test
    public void createInstanceFromUnregisteredException() {
        final JsonObject genericExceptionJson = KNOWN_JSON.toBuilder()
                .set(PolicyCommandResponse.JsonFields.PAYLOAD,
                        JsonObject.newBuilder()
                                .set(DittoRuntimeException.JsonFields.ERROR_CODE, "some.error")
                                .set(DittoRuntimeException.JsonFields.STATUS,
                                        HttpStatus.VARIANT_ALSO_NEGOTIATES.getCode())
                                .set(DittoRuntimeException.JsonFields.DESCRIPTION, "the description")
                                .set(DittoRuntimeException.JsonFields.MESSAGE, "the message")
                                .build())
                .build();

        final PolicyErrorResponse underTest =
                PolicyErrorResponse.fromJson(genericExceptionJson, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getDittoRuntimeException()).isNotNull();
        assertThat(underTest.getDittoRuntimeException().getErrorCode()).isEqualTo("some.error");
        assertThat(underTest.getDittoRuntimeException().getDescription()).contains("the description");
        assertThat(underTest.getDittoRuntimeException().getMessage()).isEqualTo("the message");
        assertThat(underTest.getDittoRuntimeException().getHttpStatus()).isEqualTo(HttpStatus.VARIANT_ALSO_NEGOTIATES);
        assertThat(underTest.getHttpStatus()).isEqualTo(HttpStatus.VARIANT_ALSO_NEGOTIATES);
    }

}
