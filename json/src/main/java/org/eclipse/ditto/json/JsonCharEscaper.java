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

import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This class implements the RFC for escaping single characters of JSON strings.
 * Additional to the RFC the unicode character for line break (U+2028) and paragraph break (U+2029) are treated as
 * control characters.
 *
 * @see "https://tools.ietf.org/html/rfc8259#section-7"
 */
@Immutable
final class JsonCharEscaper implements Function<Character, String> {

    private static final JsonCharEscaper INSTANCE = new JsonCharEscaper();

    // All chars up to this char (inclusive) are control characters.
    private static final int LAST_CONTROL_CHARACTER = 0x001F;

    private static final String QUOT_CHARS = new String(new char[]{'\\', '"'});
    private static final String BACKSLASH_CHARS = new String(new char[]{'\\', '\\'});
    private static final String LF_CHARS = new String(new char[]{'\\', 'n'});
    private static final String CR_CHARS = new String(new char[]{'\\', 'r'});
    private static final String BACKSPACE_CHARS = new String(new char[]{'\\', 'b'});
    private static final String TAB_CHARS = new String(new char[]{'\\', 't'});
    private static final String UNICODE_2028_CHARS = new String(new char[]{'\\', 'u', '2', '0', '2', '8'});
    private static final String UNICODE_2029_CHARS = new String(new char[]{'\\', 'u', '2', '0', '2', '9'});

    private JsonCharEscaper() {
        super();
    }

    /**
     * Returns an instance of {@code JsonCharEscaper}.
     *
     * @return the instance.
     */
    public static JsonCharEscaper getInstance() {
        return INSTANCE;
    }

    /**
     * Escapes the given char if necessary.
     *
     * @param c the character to be escaped.
     * @return the replacement for {@code c} or {@code null} if {@code c} does not have to be escaped.
     */
    @SuppressWarnings("OverlyComplexMethod")
    @Nullable
    @Override
    public String apply(final Character c) {
        @Nullable final String result;

        if ('"' == c) {
            result = QUOT_CHARS;
        } else if ('\n' == c) {
            result = LF_CHARS;
        } else if ('\r' == c) {
            result = CR_CHARS;
        } else if ('\\' == c) {
            result = BACKSLASH_CHARS;
        } else if ('\b' == c) {
            result = BACKSPACE_CHARS;
        } else if ('\t' == c) {
            result = TAB_CHARS;
        } else if ('\f' == c) {
            result = "\\f";
        } else if ('\u2028' == c) {
            result = UNICODE_2028_CHARS;
        } else if ('\u2029' == c) {
            result = UNICODE_2029_CHARS;
        } else if (isControlCharacter(c)) {
            result = new String(new char[]{'\\', c});
        } else {
            result = null;
        }

        return result;
    }

    private static boolean isControlCharacter(final char c) {
        return c <= LAST_CONTROL_CHARACTER;
    }

}
