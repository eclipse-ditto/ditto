/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.model.signals.commands.actions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.SubjectId;
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommandResponse;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.signals.commands.actions.ActivateTokenIntegrationResponse}.
 */
public final class ActivateTokenIntegrationResponseTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommandResponse.JsonFields.TYPE, ActivateTokenIntegrationResponse.TYPE)
            .set(PolicyCommandResponse.JsonFields.STATUS, ActivateTokenIntegrationResponse.STATUS.getCode())
            .set(PolicyCommandResponse.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ActivateTokenIntegrationResponse.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ActivateTokenIntegrationResponse.JSON_SUBJECT_IDS, JsonArray.newBuilder()
                    .add(TestConstants.Policy.SUBJECT_ID.toString())
                    .build())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivateTokenIntegrationResponse.class,
                areImmutable(),
                provided(Label.class, SubjectId.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ActivateTokenIntegrationResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ActivateTokenIntegrationResponse.of(null, TestConstants.Policy.LABEL,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ActivateTokenIntegrationResponse.of(TestConstants.Policy.POLICY_ID, null,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        ActivateTokenIntegrationResponse.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ActivateTokenIntegrationResponse underTest =
                ActivateTokenIntegrationResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ActivateTokenIntegrationResponse underTest =
                ActivateTokenIntegrationResponse.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final ActivateTokenIntegrationResponse expectedCommand =
                ActivateTokenIntegrationResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

}
