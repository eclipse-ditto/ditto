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
import org.eclipse.ditto.policies.model.signals.commands.PolicyCommand;
import org.eclipse.ditto.policies.model.signals.commands.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeactivateTokenIntegration}.
 */
public final class DeactivateTokenIntegrationTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, DeactivateTokenIntegration.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(DeactivateTokenIntegration.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(DeactivateTokenIntegration.JSON_SUBJECT_IDS, JsonArray.newBuilder()
                    .add(TestConstants.Policy.SUBJECT_ID.toString())
                    .build())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeactivateTokenIntegration.class,
                areImmutable(),
                provided(Label.class, SubjectId.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeactivateTokenIntegration.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        DeactivateTokenIntegration.of(null, TestConstants.Policy.LABEL,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        DeactivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, null,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        DeactivateTokenIntegration.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final DeactivateTokenIntegration underTest =
                DeactivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final DeactivateTokenIntegration underTest =
                DeactivateTokenIntegration.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final DeactivateTokenIntegration expectedCommand =
                DeactivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

}
