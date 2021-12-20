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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.modify.ModifyPolicyResponse}.
 */
public final class ModifyPolicyResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.CREATED.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ModifyPolicyResponse.JSON_POLICY,
                    TestConstants.Policy.POLICY.toJson(FieldType.regularOrSpecial()))
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ModifyPolicyResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, HttpStatus.NO_CONTENT.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyResponse.class,
                areImmutable(),
                provided(Policy.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyResponse underTestCreated =
                ModifyPolicyResponse.created(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyPolicyResponse underTestUpdated =
                ModifyPolicyResponse.modified(TestConstants.Policy.POLICY_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyResponse underTestCreated =
                ModifyPolicyResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getPolicyCreated()).hasValue(TestConstants.Policy.POLICY);

        final ModifyPolicyResponse underTestUpdated =
                ModifyPolicyResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getPolicyCreated()).isEmpty();
    }

    @Test
    public void parseModifyPolicyCommandResponse() {
        final ModifyPolicyResponse commandResponse =
                ModifyPolicyResponse.created(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.DITTO_HEADERS);
        final JsonObject jsonObject = commandResponse.toJson(FieldType.regularOrSpecial());

        final CommandResponse parsedCommandResponse =
                GlobalCommandResponseRegistry.getInstance().parse(jsonObject, TestConstants.DITTO_HEADERS);

        assertThat(parsedCommandResponse).isEqualTo(commandResponse);
    }

}
