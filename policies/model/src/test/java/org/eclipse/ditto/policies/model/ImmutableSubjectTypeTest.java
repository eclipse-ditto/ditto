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

        assertThat(underTest.toString()).hasToString(expected);
    }

}
