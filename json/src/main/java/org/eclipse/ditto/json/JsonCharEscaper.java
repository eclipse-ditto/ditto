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

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
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

    // All chars up to this char (inclusive) are control characters.
    private static final int LAST_CONTROL_CHARACTER = 0x001F;

    @Nullable private static JsonCharEscaper instance = null;

    // Known two-character sequence escape representations of some popular characters.
    private final Map<Character, String> replacements;

    private JsonCharEscaper() {
        replacements = new HashMap<>();
        replacements.put('"', "\\\"");
        replacements.put('\\', "\\\\");
        replacements.put('\b', "\\b");
        replacements.put('\f', "\\f");
        replacements.put('\n', "\\n");
        replacements.put('\r', "\\r");
        replacements.put('\t', "\\t");
        replacements.put('\u2028', "\\u2028");
        replacements.put('\u2029', "\\u2029");
    }

    /**
     * Returns an instance of {@code JsonCharEscaper}.
     *
     * @return the instance.
     */
    public static JsonCharEscaper getInstance() {
        JsonCharEscaper result = instance;
        if (null == result) {
            result = new JsonCharEscaper();
            instance = result;
        }
        return result;
    }

    @Override
    public String apply(final Character c) {
        final String replacement = replacements.get(requireNonNull(c));
        if (null != replacement) {
            return replacement;
        } else if (isControlCharacter(c)) {
            return "\\" + c;
        }
        return String.valueOf(c);
    }

    private static boolean isControlCharacter(final char c) {
        return c <= LAST_CONTROL_CHARACTER;
    }

}
