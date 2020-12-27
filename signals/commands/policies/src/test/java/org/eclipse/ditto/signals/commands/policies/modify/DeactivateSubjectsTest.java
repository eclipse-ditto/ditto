/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.policies.modify;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.policies.TestConstants;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DeactivateSubjects}.
 */
public final class DeactivateSubjectsTest {

    private static final List<Label> LABELS = Collections.singletonList(TestConstants.Policy.LABEL);

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(PolicyCommand.JsonFields.TYPE, DeactivateSubjects.TYPE)
            .set(PolicyCommand.JsonFields.JSON_POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(DeactivateSubjects.JsonFields.SUBJECT_ID, TestConstants.Policy.SUBJECT_ID.toString())
            .set(DeactivateSubjects.JsonFields.LABELS, JsonArray.of(TestConstants.Policy.LABEL))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeactivateSubjects.class,
                areImmutable(),
                provided(Label.class, SubjectId.class, PolicyId.class).areAlsoImmutable(),
                assumingFields("labels").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DeactivateSubjects.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        DeactivateSubjects.of(null, TestConstants.Policy.SUBJECT_ID, LABELS, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSubject() {
        DeactivateSubjects.of(TestConstants.Policy.POLICY_ID, null, LABELS, TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final DeactivateSubjects underTest =
                DeactivateSubjects.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, LABELS,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final DeactivateSubjects underTest =
                DeactivateSubjects.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        final DeactivateSubjects expectedCommand =
                DeactivateSubjects.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.SUBJECT_ID, LABELS,
                        TestConstants.EMPTY_DITTO_HEADERS);
        assertThat(underTest).isEqualTo(expectedCommand);
    }

}
