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
package org.eclipse.ditto.policies.model.signals.events;

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.Label;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.SubjectId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubjectsDeletedPartially}.
 */
public final class SubjectsDeletedPartiallyTest {

    private static final Map<Label, Collection<SubjectId>> DELETED_SUBJECT_IDS = IntStream.of(0).boxed()
            .collect(Collectors.toMap(
                    i -> TestConstants.Policy.LABEL,
                    i -> Collections.singleton(TestConstants.Policy.SUBJECT_ID)));

    private static final JsonObject DELETED_SUBJECT_IDS_JSON = JsonObject.newBuilder()
            .set(TestConstants.Policy.LABEL, JsonArray.newBuilder()
                    .add(TestConstants.Policy.SUBJECT_ID.toString())
                    .build())
            .build();

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TIMESTAMP, TestConstants.TIMESTAMP.toString())
            .set(Event.JsonFields.TYPE, SubjectsDeletedPartially.TYPE)
            .set(EventsourcedEvent.JsonFields.REVISION, TestConstants.Policy.REVISION_NUMBER)
            .set(Event.JsonFields.METADATA, TestConstants.METADATA.toJson())
            .set(PolicyEvent.JsonFields.POLICY_ID, TestConstants.Policy.POLICY_ID.toString())
            .set(SubjectsDeletedPartially.JSON_DELETED_SUBJECT_IDS, DELETED_SUBJECT_IDS_JSON)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubjectsDeletedPartially.class, areImmutable(),
                provided(Subject.class, SubjectId.class, Label.class).isAlsoImmutable(),
                assumingFields("deletedSubjectIds")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubjectsDeletedPartially.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPolicyId() {
        SubjectsDeletedPartially.of(null, DELETED_SUBJECT_IDS, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDeactivatedSubjects() {
        SubjectsDeletedPartially.of(TestConstants.Policy.POLICY_ID, null, TestConstants.Policy.REVISION_NUMBER,
                TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
    }

    @Test
    public void toJsonReturnsExpected() {
        final SubjectsDeletedPartially underTest =
                SubjectsDeletedPartially.of(TestConstants.Policy.POLICY_ID, DELETED_SUBJECT_IDS,
                        TestConstants.Policy.REVISION_NUMBER,
                        TestConstants.TIMESTAMP, TestConstants.EMPTY_DITTO_HEADERS, TestConstants.METADATA);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final SubjectsDeletedPartially underTest =
                SubjectsDeletedPartially.fromJson(KNOWN_JSON, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat((CharSequence) underTest.getPolicyEntityId()).isEqualTo(TestConstants.Policy.POLICY_ID);
        assertThat(underTest.getDeletedSubjectIds()).isEqualTo(DELETED_SUBJECT_IDS);
    }

}
