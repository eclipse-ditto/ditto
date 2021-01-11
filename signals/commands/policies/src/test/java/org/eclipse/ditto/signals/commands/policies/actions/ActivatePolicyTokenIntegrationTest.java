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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ActivatePolicyTokenIntegration}.
 */
public final class ActivatePolicyTokenIntegrationTest {

    static final List<Label> LABELS = Collections.singletonList(TestConstants.Policy.LABEL);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, ActivatePolicyTokenIntegration.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(ActivatePolicyTokenIntegration.JSON_SUBJECT_ID, TestConstants.Policy.SUBJECT_ID.toString())
            .set(ActivatePolicyTokenIntegration.JSON_EXPIRY, Instant.EPOCH.toString())
            .set(ActivatePolicyTokenIntegration.JSON_LABELS, JsonArray.of(TestConstants.Policy.LABEL))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ActivatePolicyTokenIntegration.class,
                areImmutable(),
                provided(SubjectId.class, PolicyId.class).areAlsoImmutable(),
                assumingFields("labels").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ActivatePolicyTokenIntegration.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        ActivatePolicyTokenIntegration.of(null, TestConstants.Policy.SUBJECT_ID, Instant.EPOCH, LABELS,
                TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        ActivatePolicyTokenIntegration.of(TestConstants.Policy.POLICY_ID, null, Instant.EPOCH, LABELS,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullExpiry() {
        ActivatePolicyTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, null, LABELS,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final ActivatePolicyTokenIntegration underTest =
                ActivatePolicyTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, Instant.EPOCH,
                        LABELS, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ActivatePolicyTokenIntegration underTest =
                ActivatePolicyTokenIntegration.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final ActivatePolicyTokenIntegration expectedCommand =
                ActivatePolicyTokenIntegration.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, Instant.EPOCH,
                        LABELS, TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

    @Test(expected = JsonParseException.class)
    public void tryToCreateInstanceFromInvalidTimestampInJson() {
        final JsonObject jsonWithInvalidTimestamp = KNOWN_JSON.toBuilder()
                .set(ActivatePolicyTokenIntegration.JSON_EXPIRY, "not-a-timestamp")
                .build();

        ActivatePolicyTokenIntegration.fromJson(jsonWithInvalidTimestamp, TestConstants.EMPTY_DITTO_HEADERS);
    }

}
