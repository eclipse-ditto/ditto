/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
    public void doNotEscapeSpace() {
        final String unescapedSpace = underTest.apply((char) 0x0020);

        assertThat(unescapedSpace).isNull();
    }

    @Test
    public void doNotEscapeExclamationMark() {
        final String unescapedExclamationMark = underTest.apply((char) 0x0021);

        assertThat(unescapedExclamationMark).isNull();
    }

    @Test
    public void doNotEscapeNormalCharactersBeforeBackslash() {
        final char start = (char) 0x0023;
        final char end = (char) 0x005B;

        IntStream.range(start, end)
                .mapToObj(i -> (char) i)
                .forEach(character -> {
                    final String notEscaped = underTest.apply(character);
                    assertThat(notEscaped).isNull();
                });
    }

    @Test
    public void doNotEscapeNormalCharactersAfterBackslash() {
        final char start = (char) 0x005D;
        final char end = (char) 0x10FFFF;

        IntStream.range(start, end)
                .filter(i -> '\u2028' != i && '\u2029' != i)
                .mapToObj(i -> (char) i)
                .forEach(character -> {
                    final String notEscaped = underTest.apply(character);
                    assertThat(notEscaped)
                            .describedAs("Do not escape character %s", Integer.toHexString(character))
                            .isNull();
                });
    }

    @Test
    public void escapeQuote() {
        final String escapedQuote = underTest.apply('"');

        assertThat(escapedQuote).isEqualTo("\\\"");
    }

    @Test
    public void escapeReverseSolidus() {
        final String escapedReverseSolidus = underTest.apply('\\');

        assertThat(escapedReverseSolidus).isEqualTo("\\\\");
    }

    @Test
    public void escapeBackspace() {
        final String escapedBackspace = underTest.apply('\b');

        assertThat(escapedBackspace).isEqualTo("\\b");
    }

    @Test
    public void escapeLineFeed() {
        final String escapedBackspace = underTest.apply('\n');

        assertThat(escapedBackspace).isEqualTo("\\n");
    }

    @Test
    public void escapeCarriageReturn() {
        final String escapedCarriageReturn = underTest.apply('\r');

        assertThat(escapedCarriageReturn).isEqualTo("\\r");
    }

    @Test
    public void escapeTab() {
        final String escapedTab = underTest.apply('\t');

        assertThat(escapedTab).isEqualTo("\\t");
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