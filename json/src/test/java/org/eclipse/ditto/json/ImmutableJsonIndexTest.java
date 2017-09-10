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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonIndexTest}.
 */
public final class ImmutableJsonIndexTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonIndex.class,
                areImmutable(),
                provided(CharSequence.class).isAlsoImmutable() // In this case we can make this assumption.
        );
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonIndex.class)
                .usingGetClass()
                .verify();
    }

    /** */
    @Test
    public void tryToCreateInstanceOfNullCharSequence() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ImmutableJsonIndex.of(null))
                .withMessage("The %s must not be null!", "JSON index value to be parsed")
                .withNoCause();
    }

    /** */
    @Test
    public void instanceOfEmptyCharSequenceIsPointer() {
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of("");

        assertThat(underTest.isPointer()).isTrue();
        assertThat(underTest.isKey()).isFalse();
    }

    /** */
    @Test
    public void instanceOfSlashOnlyCharSequenceIsPointer() {
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of("/");

        assertThat(underTest.isPointer()).isTrue();
        assertThat(underTest.isKey()).isFalse();
    }

    /** */
    @Test
    public void instanceOfSingleCharIsKey() {
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of("a");

        assertThat(underTest.isPointer()).isFalse();
        assertThat(underTest.isKey()).isTrue();
    }

    /** */
    @Test
    public void tryToGetKeyIfActuallyPointer() {
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of("/");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::asKey)
                .withMessage("<%s> is not a JSON key!", "/")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToGetPointerIfActuallyKey() {
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of("foo");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(underTest::asPointer)
                .withMessage("<%s> is not a JSON pointer!", "foo")
                .withNoCause();
    }

    /** */
    @Test
    public void getKeyReturnsExpected() {
        final JsonKey jsonKey = JsonFactory.newKey("foo");
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of(jsonKey);

        assertThat(underTest.asKey()).isEqualTo(jsonKey);
    }

    /** */
    @Test
    public void getPointerReturnsExpected() {
        final JsonPointer jsonPointer = JsonFactory.newPointer("/foo/bar/baz");
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of(jsonPointer);

        assertThat((Object) underTest.asPointer()).isEqualTo(jsonPointer);
    }

    /** */
    @Test
    public void toStringOfJsonPointerIndexReturnsExpected() {
        final String pointerString = "/foo/bar/baz";
        final JsonPointer jsonPointer = JsonFactory.newPointer(pointerString);
        final ImmutableJsonIndex underTest = ImmutableJsonIndex.of(jsonPointer);

        assertThat(underTest.toString()).isEqualTo(pointerString);
    }

}
