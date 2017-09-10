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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubject}.
 */
public final class ImmutableSubjectTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubject.class,
                areImmutable(),
                provided(SubjectId.class, SubjectType.class, JsonFieldDefinition.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubject.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testToAndFromJson() {
        final Subject subject =
                ImmutableSubject.of(SubjectId.newInstance(SubjectIssuer.GOOGLE_URL, "myself"), SubjectType.JWT);

        final JsonObject subjectJson = subject.toJson();
        final Subject subject1 = ImmutableSubject.fromJson(SubjectIssuer.GOOGLE_URL + ":myself", subjectJson);

        assertThat(subject).isEqualTo(subject1);
    }


    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectIssuer() {
        Subject.newInstance(null, TestConstants.Policy.SUBJECT_ID_SUBJECT, SubjectType.JWT);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubject() {
        Subject.newInstance(SubjectIssuer.GOOGLE_URL, null, SubjectType.JWT);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectId() {
        Subject.newInstance(null, SubjectType.JWT);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectType() {
        Subject.newInstance(TestConstants.Policy.SUBJECT_ID, null);
    }

    @Test
    public void createSubjectSuccess() {
        final Subject underTest = Subject.newInstance(TestConstants.Policy.SUBJECT_ID, SubjectType.JWT);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(SubjectType.JWT);
    }

    @Test
    public void createSubjectWithIssuerSuccess() {
        final Subject underTest =
                Subject.newInstance(SubjectIssuer.GOOGLE_URL, TestConstants.Policy.SUBJECT_ID_SUBJECT, SubjectType.JWT);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(TestConstants.Policy.SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(SubjectType.JWT);
    }

    @Test
    public void subjectWithIssuerEqualsSubjectAndIssuer() {
        final Subject subjectAndIssuer =
                Subject.newInstance(SubjectIssuer.GOOGLE_URL, TestConstants.Policy.SUBJECT_ID_SUBJECT, SubjectType.JWT);
        final Subject subjectWithIssuer = Subject.newInstance(TestConstants.Policy.SUBJECT_ID, SubjectType.JWT);

        assertThat(subjectWithIssuer).isEqualTo(subjectAndIssuer);
    }

}
