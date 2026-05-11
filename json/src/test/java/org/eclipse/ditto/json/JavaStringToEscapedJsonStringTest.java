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

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link JavaStringToEscapedJsonString}.
 */
public final class JavaStringToEscapedJsonStringTest {
    
    private JavaStringToEscapedJsonString underTest;

    @Before
    public void setUp() {
        underTest = JavaStringToEscapedJsonString.getInstance();
    }

    @Test
    public void convertJavaStringWithoutSpecialChars() {
        final String javaString = "Auf der Wiese blueht ein kleines Bluemelein.";
        final String expected = "\"" + javaString + "\"";

        final String actual = underTest.apply(javaString);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void convertJavaStringWithSpecialChars() {
        final String javaString = "Auf der Wiese\n blueht ein kleines \"Blümelein\".";
        final String expected = "\"" + "Auf der Wiese\\n blueht ein kleines \\\"Blümelein\\\"." + "\"";

        final String actual = underTest.apply(javaString);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void convertEmptyString() {
        assertThat(underTest.apply("")).isEqualTo("\"\"");
    }

    @Test
    public void convertOnlySpecialChars() {
        assertThat(underTest.apply("\"\\\b\f\n\r\t"))
                .isEqualTo("\"\\\"\\\\\\b\\f\\n\\r\\t\"");
    }

    @Test
    public void convertEscapeAtStart() {
        assertThat(underTest.apply("\"hello")).isEqualTo("\"\\\"hello\"");
    }

    @Test
    public void convertEscapeAtEnd() {
        assertThat(underTest.apply("hello\"")).isEqualTo("\"hello\\\"\"");
    }

    @Test
    public void convertConsecutiveEscapes() {
        assertThat(underTest.apply("a\"\"b")).isEqualTo("\"a\\\"\\\"b\"");
    }

    @Test
    public void convertControlCharsBelow0x20() {
        // 0x00..0x1F must all be escaped; shorthands take precedence over the unicode form.
        final StringBuilder input = new StringBuilder(0x20);
        for (int i = 0; i < 0x20; i++) {
            input.append((char) i);
        }
        final String actual = underTest.apply(input.toString());
        // shorthands for 0x08 \b, 0x09 \t, 0x0A \n, 0x0C \f, 0x0D \r
        final String expected = "\""
                + "\\u0000\\u0001\\u0002\\u0003\\u0004\\u0005\\u0006\\u0007"
                + "\\b\\t\\n\\u000B\\f\\r\\u000E\\u000F"
                + "\\u0010\\u0011\\u0012\\u0013\\u0014\\u0015\\u0016\\u0017"
                + "\\u0018\\u0019\\u001A\\u001B\\u001C\\u001D\\u001E\\u001F"
                + "\"";
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void surrogatePairPassthrough() {
        // 😀 = U+1F600 = surrogate pair D83D DE00. Both halves are above 0x7F and unescaped.
        final String smiley = "😀";
        assertThat(underTest.apply(smiley)).isEqualTo("\"" + smiley + "\"");
    }

    @Test
    public void backslashIsEscaped() {
        assertThat(underTest.apply("a\\b")).isEqualTo("\"a\\\\b\"");
    }

    @Test
    public void longStringWithoutEscapesTakesFastPath() {
        final StringBuilder sb = new StringBuilder(4096);
        for (int i = 0; i < 4096; i++) {
            sb.append('a');
        }
        final String input = sb.toString();
        assertThat(underTest.apply(input)).isEqualTo("\"" + input + "\"");
    }

}
