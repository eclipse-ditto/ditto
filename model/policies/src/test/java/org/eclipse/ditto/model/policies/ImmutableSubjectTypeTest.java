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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableSubjectType}.
 */
public final class ImmutableSubjectTypeTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSubjectType.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSubjectType.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "Foo";
        final SubjectType underTest = ImmutableSubjectType.of(expected);

        assertThat(underTest.toString()).isEqualTo(expected);
    }

}
