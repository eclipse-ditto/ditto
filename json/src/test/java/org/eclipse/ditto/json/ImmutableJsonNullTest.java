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

import java.lang.ref.SoftReference;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonNull}.
 */
public class ImmutableJsonNullTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonNull.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<String> red = new SoftReference<>("red");
        final SoftReference<String> black = new SoftReference<>("black");

        EqualsVerifier.forClass(ImmutableJsonNull.class) //
                .withPrefabValues(SoftReference.class, red, black) //
                .withIgnoredFields("stringRepresentation", "wrapped") //
                .withRedefinedSuperclass() //
                .verify();

        final JsonValue green = ImmutableJsonNull.newInstance();
        final JsonValue blue = ImmutableJsonNull.newInstance();

        DittoJsonAssertions.assertThat(green).isEqualTo(blue);

        final ImmutableJsonArrayNull nullArray = ImmutableJsonArrayNull.newInstance();

        Assertions.assertThat(green.equals(nullArray)).isTrue();
    }


    @Test
    public void nullBehavesAsExpected() {
        final JsonValue underTest = ImmutableJsonNull.newInstance();

        DittoJsonAssertions.assertThat(underTest).isArray();
        DittoJsonAssertions.assertThat(underTest).isNotBoolean();
        DittoJsonAssertions.assertThat(underTest).isNullLiteral();
        DittoJsonAssertions.assertThat(underTest).isNotNumber();
        DittoJsonAssertions.assertThat(underTest).isObject();
        DittoJsonAssertions.assertThat(underTest).isNotString();
        assertThat(underTest.toString()).isEqualTo("null");
    }

}

