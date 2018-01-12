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
import static org.eclipse.ditto.model.policies.TestConstants.Policy.SUBJECT_ID;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.SUBJECT_ID_SUBJECT;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.SUBJECT_ISSUER;
import static org.eclipse.ditto.model.policies.TestConstants.Policy.SUBJECT_TYPE;
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
                ImmutableSubject.of(SubjectId.newInstance(SubjectIssuer.GOOGLE, "myself"));

        final JsonObject subjectJson = subject.toJson();
        final Subject subject1 = ImmutableSubject.fromJson(SubjectIssuer.GOOGLE + ":myself", subjectJson);

        assertThat(subject).isEqualTo(subject1);
    }


    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectIssuer() {
        Subject.newInstance(null, SUBJECT_ID_SUBJECT, SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubject() {
        Subject.newInstance(SubjectIssuer.GOOGLE, null, SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectId() {
        Subject.newInstance((SubjectId) null, SUBJECT_TYPE);
    }

    @Test(expected = NullPointerException.class)
    public void createSubjectWithNullSubjectType() {
        Subject.newInstance(SUBJECT_ID, null);
    }

    @Test
    public void createSubjectSuccess() {
        final SubjectType customType = PoliciesModelFactory.newSubjectType("custom");
        final Subject underTest = Subject.newInstance(SUBJECT_ID, customType);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(customType);
    }

    @Test
    public void createSubjectWithUnknownTypeSuccess() {
        final Subject underTest = Subject.newInstance(SUBJECT_ID);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(SubjectType.UNKNOWN);
    }

    @Test
    public void createSubjectWithIssuerSuccess() {
        final Subject underTest =
                Subject.newInstance(SUBJECT_ISSUER, SUBJECT_ID_SUBJECT, SUBJECT_TYPE);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(SUBJECT_ID);
        assertThat(underTest.getType()).isEqualTo(SUBJECT_TYPE);
    }

    @Test
    public void subjectWithIssuerEqualsSubjectAndIssuer() {
        final Subject subjectAndIssuer =
                Subject.newInstance(SUBJECT_ISSUER, SUBJECT_ID_SUBJECT, SUBJECT_TYPE);
        final Subject subjectWithIssuer = Subject.newInstance(SUBJECT_ID, SUBJECT_TYPE);

        assertThat(subjectWithIssuer).isEqualTo(subjectAndIssuer);
    }

}
