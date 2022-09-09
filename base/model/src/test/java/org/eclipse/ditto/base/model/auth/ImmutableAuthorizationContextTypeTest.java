/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link org.eclipse.ditto.base.model.auth.ImmutableAuthorizationContextType}.
 */
public final class ImmutableAuthorizationContextTypeTest {

    private static final String KNOWN_TYPE_VALUE = "test-auth-context-type";

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAuthorizationContextType.class)
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAuthorizationContextType.class, areImmutable());
    }

    @Test
    public void toStringReturnsExpected() {
        final ImmutableAuthorizationContextType underTest = ImmutableAuthorizationContextType.of(KNOWN_TYPE_VALUE);

        assertThat(underTest.toString()).hasToString(KNOWN_TYPE_VALUE);
    }

    @Test
    public void lengthReturnsExpected() {
        final ImmutableAuthorizationContextType underTest = ImmutableAuthorizationContextType.of(KNOWN_TYPE_VALUE);

        assertThat(underTest.length()).isEqualTo(KNOWN_TYPE_VALUE.length());
    }

    @Test
    public void charAtReturnsExpected() {
        final byte charIndex = 3;
        final ImmutableAuthorizationContextType underTest = ImmutableAuthorizationContextType.of(KNOWN_TYPE_VALUE);

        assertThat(underTest.charAt(charIndex)).isEqualTo(KNOWN_TYPE_VALUE.charAt(charIndex));
    }

    @Test
    public void subSequenceReturnsExpected() {
        final byte sequenceStart = 5;
        final byte sequenceEnd = 11;
        final ImmutableAuthorizationContextType underTest = ImmutableAuthorizationContextType.of(KNOWN_TYPE_VALUE);

        assertThat(underTest.subSequence(sequenceStart, sequenceEnd))
                .isEqualTo(KNOWN_TYPE_VALUE.subSequence(sequenceStart, sequenceEnd));
    }

    @Test
    public void compareToWorksAsExpected() {
        final ImmutableAuthorizationContextType authCtxTypeAbc = ImmutableAuthorizationContextType.of("abc");
        final ImmutableAuthorizationContextType authCtxTypeDef = ImmutableAuthorizationContextType.of("def");

        try (final AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(authCtxTypeAbc.compareTo(authCtxTypeAbc))
                    .as("compare with equal")
                    .isZero();
            softly.assertThat(authCtxTypeAbc.compareTo(authCtxTypeDef))
                    .as("compare with greater")
                    .isNegative();
            softly.assertThat(authCtxTypeDef.compareTo(authCtxTypeAbc))
                    .as("compare with less")
                    .isPositive();
        }
    }

}
