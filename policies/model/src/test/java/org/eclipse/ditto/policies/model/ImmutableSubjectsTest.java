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
package org.eclipse.ditto.policies.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubjects}.
 */
public final class ImmutableSubjectsTest {

    private static final SubjectId KNOWN_SUBJECT_ID_1 = SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself1");
    private static final SubjectId KNOWN_SUBJECT_ID_2 = SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself2");
    private static final SubjectId KNOWN_SUBJECT_ID_3 = SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself3");
    private static final Subject SUBJECT_1 = ImmutableSubject.of(KNOWN_SUBJECT_ID_1);
    private static final Subject SUBJECT_2 = ImmutableSubject.of(KNOWN_SUBJECT_ID_2);
    private static final Subject SUBJECT_3_FOO =
            ImmutableSubject.of(KNOWN_SUBJECT_ID_3, SubjectType.newInstance("foo"));
    private static final Subject SUBJECT_3_BAR =
            ImmutableSubject.of(KNOWN_SUBJECT_ID_3, SubjectType.newInstance("bar"));

    private static final String KNOWN_SUBJECT_TYPE = "custom";
    private static final Instant KNOWN_SUBJECT_EXPIRY = Instant.now();
    private static final String KNOWN_SUBJECT_EXPIRY_STR = KNOWN_SUBJECT_EXPIRY.toString();
    private static final JsonObject KNOWN_SUBJECT_ANNOUNCEMENT_JSON = ImmutableSubjectAnnouncementTest.KNOWN_JSON;
    private static final JsonObject KNOWN_SUBJECT_JSON = JsonObject.newBuilder()
            .set(Subject.JsonFields.TYPE, KNOWN_SUBJECT_TYPE)
            .set(Subject.JsonFields.EXPIRY, KNOWN_SUBJECT_EXPIRY_STR)
            .set(Subject.JsonFields.ANNOUNCEMENT, KNOWN_SUBJECT_ANNOUNCEMENT_JSON)
            .build();

    private static final JsonObject KNOWN_SUBJECTS_JSON = JsonObject.newBuilder()
            .set(SUBJECT_1.getId(), KNOWN_SUBJECT_JSON)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjects.class,
                areImmutable(),
                assumingFields("subjects").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjects.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Collection<Subject> subjectList = Arrays.asList(SUBJECT_1, SUBJECT_2);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final JsonObject subjectsJson = underTest.toJson();
        final Subjects subjects1 = ImmutableSubjects.fromJson(subjectsJson);

        assertThat(underTest).isEqualTo(subjects1);
    }

    @Test
    public void createResourcesWithSamePathsShouldFail() {
        final Collection<Subject> subjectList = Arrays.asList(SUBJECT_1, SUBJECT_1);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ImmutableSubjects.of(subjectList))
                .withMessageStartingWith("There is more than one Subject with the ID")
                .withNoCause();
    }

    @Test
    public void setExistingSubjectAgainReturnsSameInstance() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_1);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.setSubject(SUBJECT_1);

        assertThat(underTest).isSameAs(subjects);
    }

    @Test
    public void setSubjectReturnsNewExtendedObject() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_1);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.setSubject(SUBJECT_2);

        assertThat(underTest).containsOnly(SUBJECT_1);
        assertThat(subjects).containsOnly(SUBJECT_1, SUBJECT_2);
    }

    @Test
    public void setSubjectsReturnsNewExtendedObject() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_1);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.setSubjects(Subjects.newInstance(SUBJECT_2, SUBJECT_3_FOO));

        assertThat(underTest).containsOnly(SUBJECT_1);
        assertThat(subjects).containsOnly(SUBJECT_1, SUBJECT_2, SUBJECT_3_FOO);
    }

    @Test
    public void setSubjectsWithDifferentTypeOverridesExisting() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_3_FOO);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.setSubjects(Subjects.newInstance(SUBJECT_3_BAR));

        assertThat(underTest).containsOnly(SUBJECT_3_FOO);
        assertThat(subjects).containsOnly(SUBJECT_3_BAR);
    }

    @Test
    public void setSubjectsWithDifferentTypeAreSemanticallyTheSame() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_3_FOO);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.setSubjects(Subjects.newInstance(SUBJECT_3_BAR));

        assertThat(underTest.isSemanticallySameAs(subjects)).isTrue();
    }

    @Test
    public void removeSubjectReturnsNewReducedObject() {
        final Collection<Subject> subjectList = Arrays.asList(SUBJECT_1, SUBJECT_2);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.removeSubject(SUBJECT_1.getId());

        assertThat(underTest).containsOnly(SUBJECT_1, SUBJECT_2);
        assertThat(subjects).containsOnly(SUBJECT_2);
    }

    @Test
    public void removeNonExistingSubjectReturnsSameObject() {
        final Collection<Subject> subjectList = Collections.singleton(SUBJECT_1);
        final Subjects underTest = ImmutableSubjects.of(subjectList);

        final Subjects subjects = underTest.removeSubject(SUBJECT_2.getId());

        assertThat(underTest).isSameAs(subjects);
    }

    @Test
    public void testFromJson() {
        final Subjects subjects = ImmutableSubjects.fromJson(KNOWN_SUBJECTS_JSON);
        final Subject subject1 = ImmutableSubject.fromJson(SUBJECT_1.getId(), KNOWN_SUBJECT_JSON);
        assertThat(subjects).containsOnly(subject1);
    }

}
