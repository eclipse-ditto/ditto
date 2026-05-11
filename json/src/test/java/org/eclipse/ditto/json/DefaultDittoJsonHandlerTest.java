/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.util.function.Function;

import org.junit.Test;

/**
 * Unit tests for the numeric parsing behaviour of {@link DefaultDittoJsonHandler}, exercised through
 * {@link JsonValueParser}.
 * <p>
 * The handler does not use {@link NumberFormatException} as control flow when distinguishing between {@code int} and
 * {@code long} values; these tests pin down the resulting {@link JsonValue} subtype across the
 * {@code Integer}/{@code Long} boundary and around expected error inputs.
 */
public final class DefaultDittoJsonHandlerTest {

    private static final Function<String, JsonValue> PARSER = JsonValueParser.fromString();

    private static JsonValue parseValue(final String numberLiteral) {
        return PARSER.apply("{\"a\":" + numberLiteral + "}").asObject().getValue("a").get();
    }

    @Test
    public void parsePositiveIntegerYieldsJsonInt() {
        final JsonValue value = parseValue("42");
        assertThat(value.isInt()).isTrue();
        assertThat(value.isLong()).isTrue();
        assertThat(value.asInt()).isEqualTo(42);
    }

    @Test
    public void parseNegativeIntegerYieldsJsonInt() {
        final JsonValue value = parseValue("-42");
        assertThat(value.isInt()).isTrue();
        assertThat(value.asInt()).isEqualTo(-42);
    }

    @Test
    public void parseZeroYieldsJsonInt() {
        final JsonValue value = parseValue("0");
        assertThat(value.isInt()).isTrue();
        assertThat(value.asInt()).isEqualTo(0);
    }

    @Test
    public void parseIntegerMaxValueExactYieldsJsonInt() {
        final JsonValue value = parseValue(String.valueOf(Integer.MAX_VALUE));
        assertThat(value.isInt()).isTrue();
        assertThat(value.asInt()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    public void parseIntegerMaxValuePlusOneYieldsJsonLong() {
        final long boundary = (long) Integer.MAX_VALUE + 1L;
        final JsonValue value = parseValue(String.valueOf(boundary));
        assertThat(value.isInt()).isFalse();
        assertThat(value.isLong()).isTrue();
        assertThat(value.asLong()).isEqualTo(boundary);
    }

    @Test
    public void parseIntegerMinValueExactYieldsJsonInt() {
        final JsonValue value = parseValue(String.valueOf(Integer.MIN_VALUE));
        assertThat(value.isInt()).isTrue();
        assertThat(value.asInt()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    public void parseIntegerMinValueMinusOneYieldsJsonLong() {
        final long boundary = (long) Integer.MIN_VALUE - 1L;
        final JsonValue value = parseValue(String.valueOf(boundary));
        assertThat(value.isInt()).isFalse();
        assertThat(value.isLong()).isTrue();
        assertThat(value.asLong()).isEqualTo(boundary);
    }

    @Test
    public void parseLongMaxValueExactYieldsJsonLong() {
        final JsonValue value = parseValue(String.valueOf(Long.MAX_VALUE));
        assertThat(value.isLong()).isTrue();
        assertThat(value.isInt()).isFalse();
        assertThat(value.asLong()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    public void parseLongMinValueExactYieldsJsonLong() {
        final JsonValue value = parseValue(String.valueOf(Long.MIN_VALUE));
        assertThat(value.isLong()).isTrue();
        assertThat(value.asLong()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    public void parseLong19DigitHashYieldsJsonLong() {
        // Representative input from Publisher.deserializeGroupedHashes - a 64-bit hash always overflows Integer.
        final long hash = 7719141912096836184L;
        final JsonValue value = parseValue(String.valueOf(hash));
        assertThat(value.isLong()).isTrue();
        assertThat(value.asLong()).isEqualTo(hash);
    }

    @Test
    public void parseValueAboveLongMaxValueThrowsJsonParseException() {
        // 20-digit string exceeds Long.MAX_VALUE (9223372036854775807).
        final String tooLarge = "99999999999999999999";
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> parseValue(tooLarge));
    }

    @Test
    public void parseDecimalYieldsJsonDouble() {
        final JsonValue value = parseValue("3.14");
        assertThat(value.isDouble()).isTrue();
        assertThat(value.asDouble()).isEqualTo(3.14d);
    }

    @Test
    public void parseScientificNotationYieldsJsonDouble() {
        final JsonValue value = parseValue("1e10");
        assertThat(value.isDouble()).isTrue();
        assertThat(value.asDouble()).isEqualTo(1e10d);
    }

}
