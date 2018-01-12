/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.policies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

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
    private static final Subject SUBJECT_1 = ImmutableSubject.of(KNOWN_SUBJECT_ID_1);
    private static final Subject SUBJECT_2 = ImmutableSubject.of(KNOWN_SUBJECT_ID_2);

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

    // TODO Add JSON tests

}
