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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImports;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyImportsResponse}.
 */
public final class ModifyPolicyImportsResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyImportsResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyImportsResponse.class,
                areImmutable(),
                provided(PolicyId.class, PolicyImports.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyImportsResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyImportsResponse underTest =
                ModifyPolicyImportsResponse.modified(TestConstants.Policy.POLICY_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyImportsResponse underTest =
                ModifyPolicyImportsResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
    }

}
