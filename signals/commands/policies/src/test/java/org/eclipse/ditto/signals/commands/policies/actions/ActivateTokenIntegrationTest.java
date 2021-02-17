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
package org.eclipse.ditto.signals.commands.policies.actions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ActivateTokenIntegration}.
 */
public final class ActivateTokenIntegrationTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ActivateTokenIntegration.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ActivateTokenIntegration.JSON_LABEL, TestConstants.Policy.LABEL.toString())
            .set(ActivateTokenIntegration.JSON_SUBJECT_IDS, JsonArray.newBuilder()
                    .add(TestConstants.Policy.SUBJECT_ID.toString())
                    .build())
            .set(ActivateTokenIntegration.JSON_EXPIRY, Instant.EPOCH.toString())
            .build();

    private static final SubjectId KNOWN_SECOND_SUBJECT =
            PoliciesModelFactory.newSubjectId(SubjectIssuer.GOOGLE, "another-subject");

    private static final JsonObject KNOWN_JSON_MULTIPLE_SUBJECT_IDS = KNOWN_JSON.toBuilder()
            .set(ActivateTokenIntegration.JSON_SUBJECT_IDS, JsonArray.newBuilder()
                    .add(TestConstants.Policy.SUBJECT_ID.toString())
                    .add(KNOWN_SECOND_SUBJECT.toString())
                    .build())
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivateTokenIntegration.class,
                areImmutable(),
                provided(Label.class, SubjectId.class, PolicyId.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ActivateTokenIntegration.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ActivateTokenIntegration.of(null, TestConstants.Policy.LABEL,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), Instant.EPOCH,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullLabel() {
        ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, null,
                Collections.singleton(TestConstants.Policy.SUBJECT_ID), Instant.EPOCH,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, null, Instant.EPOCH, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiry() {
        ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID,
                TestConstants.Policy.LABEL, Collections.singleton(TestConstants.Policy.SUBJECT_ID), null,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ActivateTokenIntegration underTest =
                ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), Instant.EPOCH,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void toJsonWithSeveralSubjectsReturnsExpected() {
        final ActivateTokenIntegration underTest =
                ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Arrays.asList(TestConstants.Policy.SUBJECT_ID, KNOWN_SECOND_SUBJECT), Instant.EPOCH,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON_MULTIPLE_SUBJECT_IDS);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ActivateTokenIntegration underTest =
                ActivateTokenIntegration.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final ActivateTokenIntegration expectedCommand =
                ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Collections.singleton(TestConstants.Policy.SUBJECT_ID), Instant.EPOCH,
                        TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test
    public void createInstanceFromValidJsonWithSeveralSubjects() {
        final ActivateTokenIntegration underTest =
                ActivateTokenIntegration.fromJson(KNOWN_JSON_MULTIPLE_SUBJECT_IDS, TestConstants.EMPTY_DITTO_HEADERS);

        final ActivateTokenIntegration expectedCommand =
                ActivateTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.LABEL,
                        Arrays.asList(TestConstants.Policy.SUBJECT_ID, KNOWN_SECOND_SUBJECT), Instant.EPOCH,
                        TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test(expected = JsonParseException.class)
    public void tryToCreateInstanceFromInvalidTimestampInJson() {
        final JsonObject jsonWithInvalidTimestamp = KNOWN_JSON.toBuilder()
                .set(ActivateTokenIntegration.JSON_EXPIRY, "not-a-timestamp")
                .build();

        ActivateTokenIntegration.fromJson(jsonWithInvalidTimestamp, TestConstants.EMPTY_DITTO_HEADERS);
    }

}
