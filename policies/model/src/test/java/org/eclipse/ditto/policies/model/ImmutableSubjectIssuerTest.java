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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSubjectIssuer}.
 */
public final class ImmutableSubjectIssuerTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectIssuer.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectIssuer.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void createSubjectIssuerWithNonEmptySubjectIssuerValue() {
        final String nonEmpty = "my-issuer";

        final SubjectIssuer emptySubjectIssuer = ImmutableSubjectIssuer.of(nonEmpty);

        assertThat(emptySubjectIssuer.toString()).hasToString(nonEmpty);
    }

    @Test
    public void createSubjectIssuerWithNullSubjectIssuerValueFails() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableSubjectIssuer.of(null));
    }

    @Test
    public void createSubjectIssuerWithEmptySubjectIssuerValue() {
        final String empty = "";

        final SubjectIssuer emptySubjectIssuer = ImmutableSubjectIssuer.of(empty);

        assertThat(emptySubjectIssuer.toString()).hasToString(empty);
    }

}
