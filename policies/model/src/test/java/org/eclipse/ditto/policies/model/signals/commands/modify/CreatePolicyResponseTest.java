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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse}.
 */
public final class CreatePolicyResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, CreatePolicyResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(CreatePolicyResponse.JSON_POLICY,
                    TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(CreatePolicyResponse.class,
                areImmutable(),
                provided(Policy.class, PolicyId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(CreatePolicyResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final CreatePolicyResponse underTestCreated =
                CreatePolicyResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final CreatePolicyResponse underTestCreated =
                CreatePolicyResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getPolicyCreated()).hasValue(TestConstants.Policy.POLICY);
    }

}
