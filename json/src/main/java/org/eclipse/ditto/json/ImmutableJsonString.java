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
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable JSON string.
 * It differs from a Java string by being surrounded by escaped quote characters.
 * For example the Java string {@code "foo"} would be {@code "\"foo\""} as JSON string.
 */
@Immutable
final class ImmutableJsonString extends AbstractJsonValue {

    private final String value;
    @Nullable private String stringRepresentation;

    private ImmutableJsonString(final String jsonString) {
        value = jsonString;
        stringRepresentation = null;
    }

    /**
     * Returns an instance of {@code ImmutableJsonString} which wraps the specified String.
     *
     * @param string the value of the returned instance.
     * @return the instance.
     * @throws NullPointerException if {@code string} is {@code null}.
     */
    public static ImmutableJsonString of(final String string) {
        return new ImmutableJsonString(requireNonNull(string, "The string value must not be null!"));
    }

    @Override
    public boolean isString() {
        return true;
    }

    @Override
    public String asString() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonString that = (ImmutableJsonString) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        // keep escaped string as escaping is expensive
        String result = stringRepresentation;
        if (null == result) {
            result = createStringRepresentation();
            stringRepresentation = result;
        }
        return result;
    }

    private String createStringRepresentation() {
        final StringBuilder stringBuilder = new StringBuilder(value.length() + 4);
        stringBuilder.append("\"");
        escapeValue(stringBuilder);
        stringBuilder.append("\"");
        return stringBuilder.toString();
    }

    private void escapeValue(final StringBuilder stringBuilder) {
        for (final char c : value.toCharArray()) {
            stringBuilder.append(getReplacementOrKeep(c));
        }
    }

    private static String getReplacementOrKeep(final char c) {
        final JsonStringEscaper jsonStringEscaper = JsonStringEscaper.getInstance();
        return jsonStringEscaper.apply(c);
    }

    /**
     * This class implements the RFC for escaping JSON strings.
     * Additional to the RFC the unicode character for line break (U+2028) and paragraph break (U+2029) are treated as
     * control characters.
     *
     * @see "https://tools.ietf.org/html/rfc8259#section-7"
     */
    @Immutable
    static final class JsonStringEscaper {

        // All chars up to this char (inclusive) are control characters.
        private static final int LAST_CONTROL_CHARACTER = 0x001F;

        @Nullable private static JsonStringEscaper instance = null;

        // Known two-character sequence escape representations of some popular characters.
        private final Map<Character, String> replacements;

        private JsonStringEscaper() {
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
         * Returns an instance of {@code JsonStringEscaper}.
         *
         * @return the instance.
         */
        static JsonStringEscaper getInstance() {
            JsonStringEscaper result = instance;
            if (null == result) {
                result = new JsonStringEscaper();
                instance = result;
            }
            return result;
        }

        String apply(final char c) {
            final String replacement = replacements.get(c);
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

}
