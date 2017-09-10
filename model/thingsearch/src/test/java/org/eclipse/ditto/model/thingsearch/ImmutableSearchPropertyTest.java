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
package org.eclipse.ditto.model.thingsearch;

import static org.eclipse.ditto.model.thingsearch.assertions.DittoSearchAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonPointer;
import org.junit.Before;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableSearchProperty}.
 */
public final class ImmutableSearchPropertyTest {

    private static final JsonPointer PROPERTY_PATH = TestConstants.SearchThing.MANUFACTURER_PATH;
    private static final String ACME = "ACME";
    private static final String BOSCH = "Bosch";

    private ImmutableSearchProperty underTest;

    @Before
    public void setUp() {
        underTest = ImmutableSearchProperty.of(PROPERTY_PATH);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableSearchProperty.class, areImmutable(), provided(JsonPointer.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableSearchProperty.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPropertyPath() {
        ImmutableSearchProperty.of(null);
    }

    @Test
    public void getPathReturnsExpected() {
        assertThat(underTest.getPath()).isEqualTo(PROPERTY_PATH);
    }

    @Test
    public void existsReturnsExpected() {
        assertThat(underTest.exists())
                .hasType(SearchFilter.Type.EXISTS)
                .hasNoValue()
                .hasStringRepresentation("exists(" + PROPERTY_PATH + ")");
    }

    @Test
    public void eqForBooleanReturnsExpected() {
        final boolean value = false;

        assertThat(underTest.eq(value))
                .hasType(SearchFilter.Type.EQ)
                .hasOnlyValue(value)
                .hasStringRepresentation("eq(" + PROPERTY_PATH + "," + value + ")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallEqWithNullString() {
        underTest.eq(null);
    }

    @Test
    public void eqForStringReturnsExpected() {
        assertThat(underTest.eq(BOSCH))
                .hasType(SearchFilter.Type.EQ)
                .hasOnlyValue(BOSCH)
                .hasStringRepresentation("eq(" + PROPERTY_PATH + ",\"" + BOSCH + "\")");
    }

    @Test
    public void neForIntReturnsExpected() {
        final int value = 23;

        assertThat(underTest.ne(value))
                .hasType(SearchFilter.Type.NE)
                .hasOnlyValue(value)
                .hasStringRepresentation("ne(" + PROPERTY_PATH + "," + value + ")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallNeWithNullString() {
        underTest.ne(null);
    }

    @Test
    public void neForStringReturnsExpected() {
        assertThat(underTest.ne(ACME))
                .hasType(SearchFilter.Type.NE)
                .hasOnlyValue(ACME)
                .hasStringRepresentation("ne(" + PROPERTY_PATH + ",\"" + ACME + "\")");
    }

    @Test
    public void geForLongReturnsExpected() {
        final long value = 1337L;

        assertThat(underTest.ge(value))
                .hasType(SearchFilter.Type.GE)
                .hasOnlyValue(value)
                .hasStringRepresentation("ge(" + PROPERTY_PATH + "," + value + ")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallGeWithNullString() {
        underTest.ge(null);
    }

    @Test
    public void geForStringReturnsExpected() {
        assertThat(underTest.ge(ACME))
                .hasType(SearchFilter.Type.GE)
                .hasOnlyValue(ACME)
                .hasStringRepresentation("ge(" + PROPERTY_PATH + ",\"" + ACME + "\")");
    }

    @Test
    public void gtWithDoubleReturnsExpected() {
        final double value = 23.42D;

        assertThat(underTest.gt(value))
                .hasType(SearchFilter.Type.GT)
                .hasOnlyValue(value)
                .hasStringRepresentation("gt(" + PROPERTY_PATH + "," + value + ")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallGtWithNullString() {
        underTest.gt(null);
    }

    @Test
    public void gtForStringReturnsExpected() {
        assertThat(underTest.gt(ACME))
                .hasType(SearchFilter.Type.GT)
                .hasOnlyValue(ACME)
                .hasStringRepresentation("gt(" + PROPERTY_PATH + ",\"" + ACME + "\")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallLeWithNullString() {
        underTest.le(null);
    }

    @Test
    public void leForStringReturnsExpected() {
        assertThat(underTest.le(BOSCH))
                .hasType(SearchFilter.Type.LE)
                .hasOnlyValue(BOSCH)
                .hasStringRepresentation("le(" + PROPERTY_PATH + ",\"" + BOSCH + "\")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallLtWithNullString() {
        underTest.lt(null);
    }

    @Test
    public void ltForStringReturnsExpected() {
        assertThat(underTest.lt(BOSCH))
                .hasType(SearchFilter.Type.LT)
                .hasOnlyValue(BOSCH)
                .hasStringRepresentation("lt(" + PROPERTY_PATH + ",\"" + BOSCH + "\")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallLikeWithNull() {
        underTest.like(null);
    }

    @Test
    public void likeReturnsExpected() {
        assertThat(underTest.like(BOSCH))
                .hasType(SearchFilter.Type.LIKE)
                .hasOnlyValue(BOSCH)
                .hasStringRepresentation("like(" + PROPERTY_PATH + ",\"" + BOSCH + "\")");
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallInWithNullStringForMandatoryValue() {
        underTest.in(null, ACME);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallInWithNullForOptionalValues() {
        underTest.in(BOSCH, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCallInWithNullCollection() {
        underTest.in(null);
    }

    @Test
    public void inForStringsReturnsExpected() {
        final String foo = "foo";
        final String bar = "bar";
        final String baz = "baz";

        assertThat(underTest.in(foo, bar, baz))
                .hasType(SearchFilter.Type.IN)
                .hasOnlyValue(foo, bar, baz);
    }

    @Test
    public void inForIntsReturnsExpected() {
        final int one = 1;
        final int two = 2;
        final int three = 3;

        assertThat(underTest.in(one, two, three))
                .hasType(SearchFilter.Type.IN)
                .hasOnlyValue(one, two, three)
                .hasStringRepresentation("in(" + PROPERTY_PATH + "," + one + "," + two + "," + three + ")");
    }

}
