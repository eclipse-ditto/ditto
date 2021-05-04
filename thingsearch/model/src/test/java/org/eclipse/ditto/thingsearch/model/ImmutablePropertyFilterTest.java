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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.thingsearch.model.assertions.DittoSearchAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutablePropertyFilter}.
 */
public final class ImmutablePropertyFilterTest {

    private static final JsonPointer THING_ID_PATH = Thing.JsonFields.ID.getPointer();
    private static final Collection<JsonValue> KNOWN_VALUES =
            Arrays.asList(newValue("foo"), newValue("bar"), newValue("baz"));

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutablePropertyFilter.class,
                areImmutable(),
                provided(JsonPointer.class, JsonValue.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutablePropertyFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullType() {
        ImmutablePropertyFilter.of(null, THING_ID_PATH, KNOWN_VALUES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyPath() {
        ImmutablePropertyFilter.of(SearchFilter.Type.EQ, null, KNOWN_VALUES);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullValues() {
        ImmutablePropertyFilter.of(SearchFilter.Type.EQ, THING_ID_PATH, null);
    }

    @Test
    public void getTypeReturnsExpected() {
        final SearchFilter.Type type = SearchFilter.Type.EQ;
        final ImmutablePropertyFilter underTest =
                ImmutablePropertyFilter.of(type, THING_ID_PATH, KNOWN_VALUES);

        assertThat(underTest).hasType(type);
    }

    @Test
    public void getPathReturnsExpected() {
        final ImmutablePropertyFilter underTest =
                ImmutablePropertyFilter.of(SearchFilter.Type.EQ, THING_ID_PATH, KNOWN_VALUES);

        assertThat(underTest).hasPath(THING_ID_PATH);
    }

    @Test
    public void getValuesReturnsUnmodifiableCollection() {
        final ImmutablePropertyFilter underTest =
                ImmutablePropertyFilter.of(SearchFilter.Type.EQ, THING_ID_PATH, newValue(false));
        final Collection<JsonValue> values = underTest.getValues();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> values.add(newValue(true)))
                .withMessage(null)
                .withNoCause();
    }

    @Test
    public void getValuesReturnsExpected() {
        final JsonValue valueFoo = newValue("foo");
        final JsonValue valueBar = newValue("bar");
        final JsonValue valueBaz = newValue("baz");

        final ImmutablePropertyFilter underTest = ImmutablePropertyFilter.of(SearchFilter.Type.IN,
                THING_ID_PATH, Arrays.asList(valueFoo, valueBar, valueBaz));

        assertThat(underTest).hasOnlyValue(valueFoo, valueBar, valueBaz);
    }

    @Test
    public void toStringOfInTypeFilterReturnsExpectedRepresentation() {
        final JsonValue valueFoo = newValue("foo");
        final JsonValue valueBar = newValue("bar");
        final JsonValue valueBaz = newValue("baz");

        final ImmutablePropertyFilter underTest = ImmutablePropertyFilter.of(SearchFilter.Type.IN,
                THING_ID_PATH, Arrays.asList(valueFoo, valueBar, valueBaz));

        assertThat(underTest)
                .hasStringRepresentation(
                        "in(" + THING_ID_PATH + ",\"foo\",\"bar\",\"baz\")");
    }

    @Test
    public void toStringOfExistsTypeFilterReturnsExpectedRepresentation() {
        final ImmutablePropertyFilter underTest =
                ImmutablePropertyFilter.of(SearchFilter.Type.EXISTS, THING_ID_PATH, Collections.emptySet());

        assertThat(underTest)
                .hasStringRepresentation(
                        "exists(" + THING_ID_PATH + ")");
    }

}
