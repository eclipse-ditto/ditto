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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonFieldSelector}.
 */
public final class ImmutableJsonFieldSelectorTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonFieldSelector.class,
                areImmutable(),
                assumingFields("pointers").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements(),
                provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonFieldSelector.class).verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullJsonPointers() {
        ImmutableJsonFieldSelector.of(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getPointersReturnsUnmodifiableSet() {
        final ImmutableJsonFieldSelector underTest = ImmutableJsonFieldSelector.of(Collections.emptySet());
        final Set<JsonPointer> actualPointers = underTest.getPointers();

        actualPointers.add(JsonFactory.newPointer("gehtNicht"));
    }

    @Test
    public void getSizeReturnsExpected() {
        final byte pointersCount = 7;
        final Set<JsonPointer> jsonPointers = IntStream.range(0, pointersCount)
                .mapToObj(String::valueOf)
                .map("pointer/"::concat)
                .map(JsonFactory::newPointer)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final ImmutableJsonFieldSelector underTest = ImmutableJsonFieldSelector.of(jsonPointers);

        assertThat(underTest.getSize()).isEqualTo(pointersCount);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void iteratorDoesNotAllowAlteringTheState() {
        final byte pointersCount = 7;
        final Set<JsonPointer> jsonPointers = IntStream.range(0, pointersCount)
                .mapToObj(String::valueOf)
                .map("pointer/"::concat)
                .map(JsonFactory::newPointer)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final ImmutableJsonFieldSelector underTest = ImmutableJsonFieldSelector.of(jsonPointers);
        final Iterator<JsonPointer> iterator = underTest.iterator();

        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    @Test
    public void usingJsonPointerCollectionResultsInSameToStringAsJsonFieldSelectorString() {
        final byte pointersCount = 7;
        final List<String> pointers = IntStream.range(0, pointersCount)
                .mapToObj(String::valueOf)
                .map("/pointer/"::concat)
                .collect(Collectors.toList());
        final Set<JsonPointer> jsonPointers = pointers.stream()
                .map(JsonFactory::newPointer)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final String jsonFieldSelector = pointers.stream().collect(Collectors.joining(","));

        final JsonFieldSelector underTest1 = ImmutableJsonFieldSelector.of(jsonPointers);
        final JsonFieldSelector underTest2 = JsonFactory.newFieldSelector(jsonFieldSelector,
                JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build());

        assertThat(underTest1.toString()).isEqualTo(underTest2.toString());
    }

}
