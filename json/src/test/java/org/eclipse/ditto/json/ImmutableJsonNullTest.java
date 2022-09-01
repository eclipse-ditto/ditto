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

        EqualsVerifier.forClass(ImmutableJsonNull.class)
                .withPrefabValues(SoftReference.class, red, black)
                .withRedefinedSuperclass()
                .verify();

        final JsonValue green = ImmutableJsonNull.getInstance();
        final JsonValue blue = ImmutableJsonNull.getInstance();

        DittoJsonAssertions.assertThat(green).isEqualTo(blue);

        final ImmutableJsonArrayNull nullArray = ImmutableJsonArrayNull.getInstance();

        Assertions.assertThat(green.equals(nullArray)).isTrue();
    }

    @Test
    public void nullBehavesAsExpected() {
        final JsonValue underTest = ImmutableJsonNull.getInstance();

        DittoJsonAssertions.assertThat(underTest).isArray()
                .isNotBoolean()
                .isNullLiteral()
                .isNotNumber()
                .isObject()
                .isNotString();
        assertThat(underTest.toString()).hasToString("null");
    }

}

