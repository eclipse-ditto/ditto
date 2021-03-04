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
 * <p><i>
 * All Unicode characters may be placed within the
 * quotation marks, except for the characters that MUST be escaped:
 * quotation mark, reverse solidus, and the control characters (U+0000
 * through U+001F). ---RFC-8257 section 7 "strings"
 * </i></p>
 *
 * @see "https://tools.ietf.org/html/rfc8259#section-7"
 */
@Immutable
final class JsonCharEscaper implements Function<Integer, String> {

    private static final JsonCharEscaper INSTANCE = new JsonCharEscaper();

    /**
     * All chars up to this char (inclusive) are JSON control characters and MUST be escaped.
     */
    private static final char LAST_CONTROL_CHARACTER = 0x001F;

    private static final char LAST_ASCII = 0x7F;

    private static final String[] ESCAPE_TABLE = createEscapeTable();

    private JsonCharEscaper() {}

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
     * @param i the character to be escaped.
     * @return the replacement for {@code c} or {@code null} if {@code c} does not have to be escaped.
     */
    @Nullable
    @Override
    public String apply(final Integer i) {
        if (0 <= i && i < ESCAPE_TABLE.length) {
            return ESCAPE_TABLE[i];
        } else {
            // return null for higher characters which are valid unescaped.
            return null;
        }
    }

    private static String[] createEscapeTable() {
        final String[] table = new String[LAST_ASCII + 1];
        // control characters must be escaped as unicode except the shorthands handled later
        for (int i = 0; i <= LAST_CONTROL_CHARACTER; ++i) {
            table[i] = escapeAsUnicode(i);
        }
        // non-control characters may be retained except the 2 must-escape characters handled later
        for (char c = LAST_CONTROL_CHARACTER + 1; c < table.length; ++c) {
            table[c] = null;
        }
        // control characters with shorthand
        table['\b'] = "\\b";
        table['\f'] = "\\f";
        table['\n'] = "\\n";
        table['\r'] = "\\r";
        table['\t'] = "\\t";
        // must-escape non-control characters
        table['"'] = "\\\"";
        table['\\'] = "\\\\";
        return table;
    }

    private static String escapeAsUnicode(final int i) {
        return String.format("\\u%04X", i);
    }

}
