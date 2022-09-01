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
package org.eclipse.ditto.policies.model.enforcers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.Set;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.model.enforcers.DefaultEffectedSubjects}.
 */
public final class DefaultEffectedSubjectsTest {

    private static final AuthorizationSubject GRANTED_1 = AuthorizationSubject.newInstance("granted1");
    private static final AuthorizationSubject GRANTED_2 = AuthorizationSubject.newInstance("granted2");
    private static final AuthorizationSubject REVOKED_1 = AuthorizationSubject.newInstance("revoked1");
    private static final AuthorizationSubject REVOKED_2 = AuthorizationSubject.newInstance("revoked2");
    private static final Collection<AuthorizationSubject> KNOWN_GRANTED = Sets.newSet(GRANTED_1, GRANTED_2);
    private static final Collection<AuthorizationSubject> KNOWN_REVOKED = Sets.newSet(REVOKED_1, REVOKED_2);

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEffectedSubjects.class,
                areImmutable(),
                provided(AuthorizationSubject.class).isAlsoImmutable(),
                assumingFields("granted", "revoked").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultEffectedSubjects.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToGetInstanceWithNullGranted() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultEffectedSubjects.of(null, Sets.newSet()))
                .withMessage("The granted must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToGetInstanceWithNullRevoked() {
        assertThatNullPointerException()
                .isThrownBy(() -> DefaultEffectedSubjects.of(Sets.newSet(), null))
                .withMessage("The revoked must not be null!")
                .withNoCause();
    }

    @Test
    public void getGrantedReturnsExpected() {
        final DefaultEffectedSubjects underTest = DefaultEffectedSubjects.of(KNOWN_GRANTED, KNOWN_REVOKED);

        assertThat(underTest.getGranted()).containsExactlyInAnyOrderElementsOf(KNOWN_GRANTED);
    }

    @Test
    public void getGrantedReturnsUnmodifiableSet() {
        final DefaultEffectedSubjects underTest = DefaultEffectedSubjects.of(KNOWN_GRANTED, KNOWN_REVOKED);
        final Set<AuthorizationSubject> granted = underTest.getGranted();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(granted::clear);
    }

    @Test
    public void getRevokedReturnsExpected() {
        final DefaultEffectedSubjects underTest = DefaultEffectedSubjects.of(KNOWN_GRANTED, KNOWN_REVOKED);

        assertThat(underTest.getRevoked()).containsExactlyInAnyOrderElementsOf(KNOWN_REVOKED);
    }

    @Test
    public void getRevokedReturnsUnmodifiableSet() {
        final DefaultEffectedSubjects underTest = DefaultEffectedSubjects.of(KNOWN_GRANTED, KNOWN_REVOKED);
        final Set<AuthorizationSubject> revoked = underTest.getRevoked();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> revoked.remove(REVOKED_2));
    }

}
