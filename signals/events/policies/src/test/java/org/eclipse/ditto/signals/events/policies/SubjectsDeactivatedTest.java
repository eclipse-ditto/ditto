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
package org.eclipse.ditto.signals.events.policies;

import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Label;
import org.eclipse.ditto.model.policies.Subject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubjectsDeactivated}.
 */
public final class SubjectsDeactivatedTest {

    private static final Map<Label, SubjectId> DEACTIVATED_SUBJECT_IDS = IntStream.of(0).boxed()
            .collect(Collectors.toMap(i -> TestConstants.Policy.LABEL, i -> TestConstants.Policy.SUBJECT_ID));

    private static final JsonObject DEACTIVATED_SUBJECT_IDS_JSON = JsonObject.newBuilder()
            .set(TestConstants.Policy.LABEL, JsonValue.of(TestConstants.Policy.SUBJECT_ID))
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, SubjectsDeactivated.TYPE)
            .set(Event.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(SubjectsDeactivated.JsonFields.DEACTIVATED_SUBJECT_IDS, DEACTIVATED_SUBJECT_IDS_JSON)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubjectsDeactivated.class, areImmutable(),
                provided(Subject.class, SubjectId.class, Label.class).isAlsoImmutable(),
                assumingFields("deactivatedSubjectIds")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectsDeactivated.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        SubjectsDeactivated.of(null, DEACTIVATED_SUBJECT_IDS, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDeactivatedSubjects() {
        SubjectsDeactivated.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.EMPTY_DITTO_HEADERS);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SubjectsDeactivated underTest =
                SubjectsDeactivated.of(TestConstants.Policy.POLICY_ID, DEACTIVATED_SUBJECT_IDS,
                        TestConstants.Policy.REVISION_NUMBER, TestConstants.TIMESTAMP,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SubjectsDeactivated underTest =
                SubjectsDeactivated.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getDeactivatedSubjectIds()).isEqualTo(DEACTIVATED_SUBJECT_IDS);
    }

}
