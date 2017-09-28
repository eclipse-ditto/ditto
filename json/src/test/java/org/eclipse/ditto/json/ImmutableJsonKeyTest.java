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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ImmutableJsonKey}.
 */
public final class ImmutableJsonKeyTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonKey.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonKey.class).suppress(Warning.NULL_FIELDS).verify();
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullKeyValue() {
        ImmutableJsonKey.of(null);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateInstanceWithEmptyKeyValue() {
        ImmutableJsonKey.of("");
    }

    /** */
    @Test
    public void toStringReturnsExpected() {
        final String expected = "key";
        final JsonKey underTest = ImmutableJsonKey.of(expected);

        assertThat(underTest.toString()).isEqualTo(expected);
    }

    @Test
    public void getKeyWithSlashesAsPointer() {
        final String keyValue = "foo/bar/baz";
        final JsonKey underTest = ImmutableJsonKey.of(keyValue);
        final JsonPointer expected = JsonFactory.newPointer(underTest);

        final JsonPointer jsonPointer = underTest.asPointer();

        assertThat(jsonPointer).hasLevelCount(1);
        assertThat(jsonPointer).isEqualTo(expected);
    }

}
