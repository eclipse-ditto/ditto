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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.restriction.LengthRestrictionTestBase;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubjectId}.
 */
public final class ImmutableSubjectIdTest extends LengthRestrictionTestBase {

    private static final String ISSUER_WITH_SEPARATOR = SubjectIssuer.GOOGLE.toString() + ":";

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectId.class, areImmutable(), provided(SubjectIssuer.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectId.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void createNewSubjectIdWithNullValue() {
        SubjectId.newInstance(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createNewSubjectIdWithEmptyValue() {
        SubjectId.newInstance("");
    }

    @Test
    public void createNewSubjectIdSuccess() {
        final String KNOWN_SUBJECT = "subject1";

        final SubjectId underTest = SubjectId.newInstance(SubjectIssuer.GOOGLE, KNOWN_SUBJECT);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getIssuer()).isEqualTo(SubjectIssuer.GOOGLE);
        assertThat(underTest.getSubject()).isEqualTo(KNOWN_SUBJECT);
    }

    @Test
    public void ignoresUrlDelimiter() {
        final SubjectId underTest = ImmutableSubjectId.of("://abc:def");

        assertThat(underTest.getIssuer().toString()).isEqualTo("://abc");
        assertThat(underTest.getSubject()).isEqualTo("def");
    }

    @Test
    public void subjectIdCanHaveMaximumLengthOf256Characters() {
        final String subjectIdWithMaximumLength = generateStringWithMaxLength(ISSUER_WITH_SEPARATOR);
        final SubjectId subjectId = ImmutableSubjectId.of(subjectIdWithMaximumLength);
        assertThat(subjectId.toString()).isEqualTo(subjectIdWithMaximumLength);
    }

    @Test
    public void subjectIdCannotHaveMoreThan256Characters() {
        final String invalidSubjectId = generateStringExceedingMaxLength(ISSUER_WITH_SEPARATOR);
        assertThatExceptionOfType(SubjectIdInvalidException.class)
                .isThrownBy(() -> ImmutableSubjectId.of(invalidSubjectId));
    }

    @Test
    public void createInvalidAttribute() {
        final String invalidSubjectId = "invalidSubjectID\u0001";
        assertThatExceptionOfType(SubjectIdInvalidException.class)
                .isThrownBy(() -> ImmutableSubjectId.of(invalidSubjectId));
    }

    @Test
    public void handlesNonUrlIssuer() {
        final SubjectId underTest = ImmutableSubjectId.of("abc:def");
        assertThat(underTest.getIssuer().toString()).isEqualTo("abc");
        assertThat(underTest.getSubject()).isEqualTo("def");
    }

    @Test
    public void newSubjectIdWithPlaceholderInputCreatesSubjectIdWithEmptyIssuerAndPlaceholderInputAsSubject() {
        final String placeholderInput = "a{{ prefix:name }}z";
        final SubjectId underTest = ImmutableSubjectId.of(placeholderInput);

        assertThat(underTest.toString()).isEqualTo(placeholderInput);
        assertThat(underTest.getIssuer().toString()).isEqualTo("");
        assertThat(underTest.getSubject()).isEqualTo(placeholderInput);
    }

}
