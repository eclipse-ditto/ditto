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
package org.eclipse.ditto.thingsearch.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSortOptionEntry}.
 */
public final class ImmutableSortOptionEntryTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSortOptionEntry.class, areImmutable(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSortOptionEntry.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateAscendingEntryWithNullPath() {
        ImmutableSortOptionEntry.asc(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateDescendingEntryWithNullPath() {
        ImmutableSortOptionEntry.desc(null);
    }

    @Test
    public void getPropertyPathReturnsExpected() {
        final ImmutableSortOptionEntry underTest =
                ImmutableSortOptionEntry.asc(TestConstants.SearchThing.MANUFACTURER_PATH);

        assertThat((Object) underTest.getPropertyPath()).isEqualTo(TestConstants.SearchThing.MANUFACTURER_PATH);
    }

    @Test
    public void ascendingSortOptionEntryHasExpectedSortOrder() {
        final ImmutableSortOptionEntry underTest =
                ImmutableSortOptionEntry.asc(TestConstants.SearchThing.MANUFACTURER_PATH);

        assertThat(underTest.getOrder()).isSameAs(SortOptionEntry.SortOrder.ASC);
    }

    @Test
    public void ascendingSortOptionEntryHasExpectedStringRepresentation() {
        final ImmutableSortOptionEntry underTest =
                ImmutableSortOptionEntry.asc(TestConstants.SearchThing.MANUFACTURER_PATH);

        assertThat(underTest.toString()).hasToString("+" + TestConstants.SearchThing.MANUFACTURER_PATH);
    }

    @Test
    public void descendingSortOptionEntryHasExpectedSortOrder() {
        final ImmutableSortOptionEntry underTest =
                ImmutableSortOptionEntry.desc(TestConstants.SearchThing.MANUFACTURER_PATH);

        assertThat(underTest.getOrder()).isSameAs(SortOptionEntry.SortOrder.DESC);
    }

    @Test
    public void descendingSortOptionEntryHasExpectedStringRepresentation() {
        final ImmutableSortOptionEntry underTest =
                ImmutableSortOptionEntry.desc(TestConstants.SearchThing.MANUFACTURER_PATH);

        assertThat(underTest.toString()).hasToString("-" + TestConstants.SearchThing.MANUFACTURER_PATH);
    }

}
