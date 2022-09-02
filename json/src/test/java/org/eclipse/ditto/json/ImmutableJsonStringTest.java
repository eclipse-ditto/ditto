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

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.eclipsesource.json.Json;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableJsonString}.
 */
public final class ImmutableJsonStringTest {

    private static final String KNOWN_STRING_VALUE = "foo";

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableJsonString.class,
                areImmutable(),
                assumingFields("stringRepresentation").areModifiedAsPartOfAnUnobservableCachingStrategy());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableJsonString.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .withIgnoredFields("stringRepresentation")
                .withNonnullFields("value")
                .verify();
    }

    @Test
    public void tryToCreateInstanceFromNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> ImmutableJsonString.of(null))
                .withMessage("The string value must not be null!")
                .withNoCause();
    }

    @Test
    public void immutableJsonStringIsNothingElse() {
        final ImmutableJsonString underTest = ImmutableJsonString.of(KNOWN_STRING_VALUE);

        assertThat(underTest).isString();
        assertThat(underTest).isNotNullLiteral();
        assertThat(underTest).isNotBoolean();
        assertThat(underTest).isNotNumber();
        assertThat(underTest).isNotArray();
        assertThat(underTest).isNotObject();
        assertThat(underTest.isInt()).isFalse();
        assertThat(underTest.isLong()).isFalse();
        assertThat(underTest.isDouble()).isFalse();
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
        final com.eclipsesource.json.JsonValue underTest = Json.value(KNOWN_STRING_VALUE);

        assertThat(underTest.toString()).hasToString(expected);
    }

    @Test
    public void asStringReturnsExpected() {
        final ImmutableJsonString underTest = ImmutableJsonString.of(KNOWN_STRING_VALUE);

        assertThat(underTest.asString()).isEqualTo(KNOWN_STRING_VALUE);
    }

}
