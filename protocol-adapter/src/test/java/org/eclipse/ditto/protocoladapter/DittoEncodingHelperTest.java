/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;

import org.junit.Test;

/**
 * Tests {@link DittoEncodingHelper}.
 */
public final class DittoEncodingHelperTest {

    @Test
    public void testNameWithoutSpecialCharacters() {
        assertEncodingAndDecoding("Ditto.is:the-greatest!we_promise&testing@a[cool]string'(makes)it$fun;answer=42");
    }

    @Test
    public void testNameWithSlash() {
        assertEncodingAndDecoding("hello/world", "hello%2Fworld");
    }

    @Test
    public void testNameWithQuestionMark() {
        assertEncodingAndDecoding("anybody-out-there?", "anybody-out-there%3F");
    }

    @Test
    public void testNameWithHashMark() {
        assertEncodingAndDecoding("#LikeABosch", "%23LikeABosch");
    }

    @Test
    public void testNameWithAsterisk() {
        assertEncodingAndDecoding("this*is*greatness", "this%2Ais%2Agreatness");
    }

    @Test
    public void testNameWithComma() {
        assertEncodingAndDecoding("speaking-without.and,", "speaking-without.and%2C");
    }

    @Test
    public void testNameWithPercent() {
        assertEncodingAndDecoding("Ditto-always-gives-110%.really!", "Ditto-always-gives-110%25.really!");
    }

    @Test
    public void testNameWithWhitespace() {
        assertEncodingAndDecoding("This is a normal sentence and not a name.", "This%20is%20a%20normal%20sentence%20and%20not%20a%20name.");
    }

    private void assertEncodingAndDecoding(final String testString) {

        assertEncodingAndDecoding(testString, testString);
    }

    private void assertEncodingAndDecoding(final String testString, final String expectedEncoded) {

        final String encoded = DittoEncodingHelper.encode(testString);
        assertThat(encoded).isEqualTo(expectedEncoded);

        assertThat(DittoEncodingHelper.decode(encoded))
                .isEqualTo(testString);

        assertThat(URLDecoder.decode(encoded)) // the URLDecoder must produce the same decoding - it decodes even more characters
                .isEqualTo(testString);
    }
}
