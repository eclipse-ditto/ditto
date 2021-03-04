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
package org.eclipse.ditto.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonArrayBuilder}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableJsonArrayBuilderTest {

    private static final JsonValue KNOWN_VALUE_FOO = JsonFactory.newValue("Foo");
    private static final JsonValue KNOWN_VALUE_INT = JsonFactory.newValue(42);

    private ImmutableJsonArrayBuilder underTest;

    @Before
    public void setUp() {
        underTest = ImmutableJsonArrayBuilder.newInstance();
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonArrayBuilder.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherBooleanNull() {
        underTest.add(true, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherIntegerNull() {
        underTest.add(1, (Integer) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherLongNull() {
        underTest.add(1, (Long) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddFurtherDoubleNull() {
        underTest.add(1.1, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddStringNull() {
        underTest.add((String) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddJsonValueNull() {
        underTest.add((JsonValue) null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddAllJsonValueNull() {
        underTest.addAll(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveWithNull() {
        underTest.remove(null);
    }

    @Test
    public void getValueAtValidPositionReturnsExpected() {
        final String bar = "bar";
        underTest.add(KNOWN_VALUE_FOO.asString(), bar, "baz");

        assertThat(underTest.get(1)).contains(JsonFactory.newValue(bar));
    }

    @Test
    public void getValueAtInvalidPositionReturnsEmptyOptional() {
        underTest.add(KNOWN_VALUE_INT);

        assertThat(underTest.get(1)).isEmpty();
    }

    @Test
    public void tryToSetBooleanValueAtInvalidPosition() {
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> underTest.set(2, true))
                .withNoCause();
    }

    @Test
    public void setValueAtValidPositionWorksAsExpected() {
        final JsonValue value = JsonFactory.newValue(23);
        final int position = 1;
        underTest.add(KNOWN_VALUE_FOO.asString(), "bar", "baz");

        underTest.set(position, value);

        assertThat(underTest.get(position)).contains(value);
    }

    @Test
    public void tryToSetNullString() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.set(0, (String) null))
                .withMessage("The %s must not be null!", "value to be set")
                .withNoCause();
    }

    @Test
    public void tryToSetNullJsonValue() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.set(0, (JsonValue) null))
                .withMessage("The %s must not be null!", "value to be set")
                .withNoCause();
    }

    @Test
    public void tryToRemoveValueAtInvalidPosition() {
        underTest.add(KNOWN_VALUE_FOO);

        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> underTest.remove(3))
                .withNoCause();
    }

    @Test
    public void removeValueAtValidPositionWorksAsExpected() {
        final int position = 1;
        underTest.add(true);
        underTest.add(KNOWN_VALUE_INT);
        underTest.add(KNOWN_VALUE_FOO);

        underTest.remove(position);

        assertThat(underTest).hasSize(2);
        assertThat(underTest.get(position)).contains(KNOWN_VALUE_FOO);
    }

    @Test
    public void getSizeReturnsExpected() {
        underTest.add(KNOWN_VALUE_INT);
        underTest.add(KNOWN_VALUE_FOO);

        assertThat(underTest.getSize()).isEqualTo(2);
    }

    @Test
    public void isEmptyWorksAsExpected() {
        assertThat(underTest.isEmpty()).isTrue();

        underTest.add(KNOWN_VALUE_FOO);

        assertThat(underTest.isEmpty()).isFalse();
    }

    @Test
    public void buildReturnsExpected() {
        underTest.add(KNOWN_VALUE_FOO);
        underTest.add(KNOWN_VALUE_INT);

        final JsonArray jsonArray = underTest.build();

        assertThat(jsonArray).containsExactly(KNOWN_VALUE_FOO, KNOWN_VALUE_INT);
    }

}
