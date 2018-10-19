/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

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

        assertThat(emptySubjectIssuer.toString()).isEqualTo(nonEmpty);
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

        assertThat(emptySubjectIssuer.toString()).isEqualTo(empty);
    }

}
