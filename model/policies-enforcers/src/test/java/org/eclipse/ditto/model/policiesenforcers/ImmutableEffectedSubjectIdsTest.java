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
package org.eclipse.ditto.model.policiesenforcers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableEffectedSubjectIds}.
 */
public final class ImmutableEffectedSubjectIdsTest {

    private static final Collection<String> GRANTED = Arrays.asList("granted1", "granted2");
    private static final Collection<String> REVOKED = Arrays.asList("revoked1", "revoked2");

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableEffectedSubjectIds.class,
                areImmutable(),
                assumingFields("grantedSubjectIds",
                        "revokedSubjectIds").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEffectedSubjectIds.class)
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void createNewInstanceWithNullGrantedSubjectIds() {
        ImmutableEffectedSubjectIds.of(null, Collections.emptySet());
    }

    @Test(expected = NullPointerException.class)
    public void createNewInstanceWithNullRevokedSubjectIds() {
        ImmutableEffectedSubjectIds.of(Collections.emptySet(), null);
    }

    @Test(expected = NullPointerException.class)
    public void createNewGrantedInstanceWithNullSubjectIds() {
        ImmutableEffectedSubjectIds.ofGranted(null);
    }

    @Test(expected = NullPointerException.class)
    public void createNewRevokedInstanceWithNullSubjectIds() {
        ImmutableEffectedSubjectIds.ofRevoked(null);
    }

    @Test
    public void createNewInstanceWithBothGrantedAndRevokedSubjectIds() {
        final EffectedSubjectIds effectedSubjectIds = ImmutableEffectedSubjectIds.of(GRANTED, REVOKED);
        assertThat(effectedSubjectIds.getGranted()).hasSameElementsAs(GRANTED);
        assertThat(effectedSubjectIds.getRevoked()).hasSameElementsAs(REVOKED);
    }

    @Test
    public void createNewInstanceWithGrantedSubjectIds() {
        final EffectedSubjectIds effectedSubjectIds = ImmutableEffectedSubjectIds.ofGranted(GRANTED);
        assertThat(effectedSubjectIds.getGranted()).hasSameElementsAs(GRANTED);
        assertThat(effectedSubjectIds.getRevoked()).isEmpty();
    }

    @Test
    public void createNewInstanceWithRevokedSubjectIds() {
        final EffectedSubjectIds effectedSubjectIds = ImmutableEffectedSubjectIds.ofRevoked(REVOKED);
        assertThat(effectedSubjectIds.getGranted()).isEmpty();
        assertThat(effectedSubjectIds.getRevoked()).hasSameElementsAs(REVOKED);
    }
}
