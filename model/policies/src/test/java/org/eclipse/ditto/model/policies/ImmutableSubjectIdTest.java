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

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubjectId}.
 */
public final class ImmutableSubjectIdTest {

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
    public void handlesNonUrlIssuer() {
        final SubjectId underTest = ImmutableSubjectId.of("abc:def");
        assertThat(underTest.getIssuer().toString()).isEqualTo("abc");
        assertThat(underTest.getSubject()).isEqualTo("def");
    }
}
