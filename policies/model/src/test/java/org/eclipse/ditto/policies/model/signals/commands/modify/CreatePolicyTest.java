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
package org.eclipse.ditto.policies.model.signals.commands.modify;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.PoliciesModelFactory;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyIdInvalidException;
import org.eclipse.ditto.policies.model.PolicyImportInvalidException;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy}.
 */
public final class CreatePolicyTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, CreatePolicy.TYPE)
            .set(CreatePolicy.JSON_POLICY, TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(CreatePolicy.class,
                areImmutable(),
                provided(Policy.class, JsonObject.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CreatePolicy.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicy() {
        CreatePolicy.of(null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = PolicyIdInvalidException.class)
    public void tryToCreateInstanceWithInvalidPolicyId() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test.ns:foo/bar"))
                .set(TestConstants.Policy.POLICY_ENTRY)
                .build();

        CreatePolicy.of(policy, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void createInstanceWithValidPolicyId() {
        final Policy policy = PoliciesModelFactory.newPolicyBuilder(PolicyId.of("test.ns:foo-bar"))
                .set(TestConstants.Policy.POLICY_ENTRY)
                .build();

        final CreatePolicy createPolicy =
                CreatePolicy.of(policy, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(createPolicy).isNotNull();
    }

    @Test(expected = PolicyImportInvalidException.class)
    public void tryToCreateInstanceWithSelfReference() {
        CreatePolicy.of(TestConstants.Policy.POLICY
                        .toBuilder()
                        .setPolicyImport(TestConstants.Policy.getPolicyImport(TestConstants.Policy.POLICY_ID))
                        .build(),
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final CreatePolicy underTest =
                CreatePolicy.of(TestConstants.Policy.POLICY, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final CreatePolicy underTest =
                CreatePolicy.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getPolicy()).isEqualTo(TestConstants.Policy.POLICY);
    }

}
