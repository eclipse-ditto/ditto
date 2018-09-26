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
package org.eclipse.ditto.model.thingsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSortOption}.
 */
public final class ImmutableSortOptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSortOption.class,
                areImmutable(),
                provided(SortOptionEntry.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSortOption.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullEntries() {
        ImmutableSortOption.of(null);
    }

    @Test
    public void stringRepresentationOfZeroEntriesIsExpected() {
        final String expected = "";
        final ImmutableSortOption underTest = ImmutableSortOption.of(Collections.emptyList());

        assertThat(underTest.toString()).isEqualTo(expected);
    }

    @Test
    public void stringRepresentationIsExpected() {
        final SortOptionEntry thingIdSortOptionEntry =
                ImmutableSortOptionEntry.asc(JsonFactory.newPointer("thingId"));
        final SortOptionEntry manufacturerSortOptionEntry =
                ImmutableSortOptionEntry.desc(JsonFactory.newPointer("/attributes/manufacturer"));

        final ImmutableSortOption underTest =
                ImmutableSortOption.of(Arrays.asList(thingIdSortOptionEntry, manufacturerSortOptionEntry));

        final String expected = "sort(+/thingId,-/attributes/manufacturer)";

        assertThat(underTest.toString()).isEqualTo(expected);
    }

    @Test
    public void sortOptionWithoutEntriesIsEmpty() {
        final ImmutableSortOption underTest = ImmutableSortOption.of(Collections.emptyList());

        assertThat(underTest).isEmpty();
        assertThat(underTest.getSize()).isZero();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getEntriesReturnsAnUnmodifiableCollection() {
        final SortOptionEntry sortOptionEntry = mock(SortOptionEntry.class);
        final ImmutableSortOption underTest = ImmutableSortOption.of(Collections.singletonList(sortOptionEntry));
        final Collection<SortOptionEntry> entries = underTest.getEntries();
        entries.remove(sortOptionEntry);
    }

}
