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

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ImmutableJsonBoolean}.
 */
public final class ImmutableJsonLiteralTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonBoolean.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonValue> red = new SoftReference<>(ImmutableJsonBoolean.TRUE);
        final SoftReference<JsonValue> black = new SoftReference<>(ImmutableJsonBoolean.FALSE);

        EqualsVerifier.forClass(ImmutableJsonBoolean.class)
                .withRedefinedSuperclass()
                .withPrefabValues(SoftReference.class, red, black)
                .suppress(Warning.REFERENCE_EQUALITY)
                .verify();
    }

    @Test
    public void trueBehavesAsExpected() {
        final ImmutableJsonBoolean underTest = ImmutableJsonBoolean.TRUE;

        assertThat(underTest).isNotArray()
                .isBoolean()
                .isNotNullLiteral()
                .isNotNumber()
                .isNotObject()
                .isNotString();
        assertThat(underTest.toString()).hasToString("true");
    }

    @Test
    public void falseBehavesAsExpected() {
        final ImmutableJsonBoolean underTest = ImmutableJsonBoolean.FALSE;

        assertThat(underTest).isNotArray()
                .isBoolean()
                .isNotNullLiteral()
                .isNotNumber()
                .isNotObject()
                .isNotString();
        assertThat(underTest.toString()).hasToString("false");
    }

}
