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

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link ImmutableJsonString}.
 */
public final class ImmutableJsonStringTest {

    private static final String KNOWN_STRING_VALUE = "foo";
    private static final com.eclipsesource.json.JsonValue KNOWN_JSON_STRING =
            com.eclipsesource.json.Json.value(KNOWN_STRING_VALUE);

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonString.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        final SoftReference<JsonValue> red = new SoftReference<>(JsonFactory.newValue("red"));
        final SoftReference<JsonValue> black = new SoftReference<>(JsonFactory.newValue("black"));

        EqualsVerifier.forClass(ImmutableJsonString.class) //
                .withIgnoredFields("stringRepresentation") //
                .withRedefinedSuperclass() //
                .withPrefabValues(SoftReference.class, red, black) //
                .suppress(Warning.REFERENCE_EQUALITY) //
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceFromNullValue() {
        ImmutableJsonString.of(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToCreateInstanceFromNonStringValue() {
        final com.eclipsesource.json.JsonValue booleanJsonValue = com.eclipsesource.json.Json.value(false);
        ImmutableJsonString.of(booleanJsonValue);
    }

    @Test
    public void immutableJsonStringIsNothingElse() {
        final ImmutableJsonString underTest = ImmutableJsonString.of(KNOWN_JSON_STRING);

        assertThat(underTest).isString();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotNumber();
        assertThat(underTest).isNotArray();
        assertThat(underTest).isNotObject();
        assertThat(underTest).doesNotSupport(JsonValue::asBoolean);
        assertThat(underTest).doesNotSupport(JsonValue::asInt);
        assertThat(underTest).doesNotSupport(JsonValue::asLong);
        assertThat(underTest).doesNotSupport(JsonValue::asDouble);
        assertThat(underTest).doesNotSupport(JsonValue::asArray);
        assertThat(underTest).doesNotSupport(JsonValue::asObject);
    }

    @Test
    public void toStringReturnsExpected() {
        final String expected = "\"" + KNOWN_STRING_VALUE + "\"";
        final ImmutableJsonString underTest = ImmutableJsonString.of(KNOWN_JSON_STRING);

        assertThat(underTest.toString()).isEqualTo(expected);
    }

    @Test
    public void asStringReturnsExpected() {
        final ImmutableJsonString underTest = ImmutableJsonString.of(KNOWN_JSON_STRING);

        assertThat(underTest.asString()).isEqualTo(KNOWN_STRING_VALUE);
    }

}
