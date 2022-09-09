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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Test;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonValue;

/**
 * Unit test for {@link JsonCharEscaper}.
 * Test cases are oriented on https://tools.ietf.org/html/rfc8259#section-7
 */
public final class JsonCharEscaperTest {

    private static final String SHORTHAND_CONTROL_CHARACTERS = "\b\f\n\r\t";

    private static final String SHORTHAND_CONTROL_CHARACTER_NAMES = "bfnrt";

    private JsonCharEscaper underTest;

    @Before
    public void setUp() {
        underTest = JsonCharEscaper.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonCharEscaper.class,
                areImmutable(),
                assumingFields("replacements").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void doNotEscapeNormalCharactersBeforeBackslash() {
        final int start = 0x0020; // ' '
        final int end = 0x00FFFF;

        IntStream.range(start, end)
                .filter(i -> i != '"' && i != '\\')
                .forEach(character -> {
                    final String notEscaped = underTest.apply(character);
                    assertThat(notEscaped)
                            .isNull();
                });
    }

    @Test
    public void escapeQuote() {
        final String escapedQuote = underTest.apply((int) '"');

        assertThat(escapedQuote).isEqualTo("\\\"");
    }

    @Test
    public void escapeReverseSolidus() {
        final String escapedReverseSolidus = underTest.apply((int) '\\');

        assertThat(escapedReverseSolidus).isEqualTo("\\\\");
    }

    @Test
    public void escapeControlCharactersWithoutShorthands() {
        IntStream.range(0, 0x1F).forEach(character -> {
            if (SHORTHAND_CONTROL_CHARACTERS.chars().noneMatch(i -> i == character)) {
                final String unicodeEscaped = underTest.apply(character);
                assertThat(unicodeEscaped)
                        .describedAs("Do not escape character %s", Integer.toHexString(character))
                        .startsWith("\\u");
                assertThat(Integer.parseInt(unicodeEscaped.substring(2), 16)).isEqualTo(character);
            }
        });
    }

    @Test
    public void escapeControlCharactersWithShorthands() {
        SHORTHAND_CONTROL_CHARACTERS.chars().forEach(character -> {
            final String escaped = underTest.apply(character);
            final String expected =
                    "\\" + SHORTHAND_CONTROL_CHARACTER_NAMES.charAt(SHORTHAND_CONTROL_CHARACTERS.indexOf(character));
            assertThat(escaped).isEqualTo(expected);
        });
    }

    @Test
    public void minimalJsonString() {
        final String rawString = "";
        final JsonValue jsonValue = Json.value(rawString);

        final String actualOwn = ImmutableJsonString.of(rawString).toString();
        final String actual = jsonValue.toString();

        assertThat(actualOwn).isEqualTo(actual);
    }

}
