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
package org.eclipse.ditto.model.base.auth;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableAuthorizationSubject}.
 */
public final class ImmutableAuthorizationSubjectTest {

    private static final String KNOWN_ID = "antman";


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAuthorizationSubject.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAuthorizationSubject.class)
                .usingGetClass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateAuthorizationSubjectWithNullId() {
        ImmutableAuthorizationSubject.of(null);
    }


    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateAuthorizationSubjectWithEmptyId() {
        ImmutableAuthorizationSubject.of("");
    }


    @Test
    public void getIdReturnsExpected() {
        final AuthorizationSubject underTest = ImmutableAuthorizationSubject.of(KNOWN_ID);

        assertThat(underTest.getId()).isEqualTo(KNOWN_ID);
    }


    @Test
    public void toStringReturnsSameAsGetId() {
        final ImmutableAuthorizationSubject underTest = ImmutableAuthorizationSubject.of(KNOWN_ID);

        assertThat(underTest.toString()).isEqualTo(underTest.getId());
    }

}
