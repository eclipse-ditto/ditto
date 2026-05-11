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

import static java.util.Objects.requireNonNull;

import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This class converts a Java String into an escaped JSON string.
 * The JSON string is surrounded by {@code "} and escaped with the help of
 * {@link org.eclipse.ditto.json.JsonCharEscaper}.
 */
@Immutable
final class JavaStringToEscapedJsonString implements UnaryOperator<String> {

    private static final JavaStringToEscapedJsonString INSTANCE =
            new JavaStringToEscapedJsonString(JsonCharEscaper.getInstance());

    private static final int NUM_ENCLOSING_QUOTES = 2;
    private static final double ESCAPING_BUFFER_FACTOR;

    private static final char QUOTE = '\"';

    static {
        ESCAPING_BUFFER_FACTOR = Double.parseDouble(
                System.getProperty("ditto.json.escaping-buffer-factor", "1.5")
        );
    }

    private final IntFunction<String> jsonCharEscaper;

    private JavaStringToEscapedJsonString(final IntFunction<String> theJsonCharEscaper) {
        jsonCharEscaper = theJsonCharEscaper;
    }

    /**
     * Returns an instance of {@code JavaStringToEscapedJsonString}.
     *
     * @return the instance.
     */
    public static JavaStringToEscapedJsonString getInstance() {
        return INSTANCE;
    }

    @Override
    public String apply(final String javaString) {
        requireNonNull(javaString, "The Java String to be converted must not be null");
        final int len = javaString.length();
        final int firstEscape = indexOfFirstEscape(javaString, len);
        if (firstEscape < 0) {
            final StringBuilder stringBuilder = new StringBuilder(len + NUM_ENCLOSING_QUOTES);
            stringBuilder.append(QUOTE).append(javaString).append(QUOTE);
            return stringBuilder.toString();
        }
        final StringBuilder stringBuilder =
                new StringBuilder((int) (len * ESCAPING_BUFFER_FACTOR) + NUM_ENCLOSING_QUOTES);
        stringBuilder.append(QUOTE);
        stringBuilder.append(javaString, 0, firstEscape);
        int runStart = firstEscape;
        for (int i = firstEscape; i < len; i++) {
            @Nullable final String replacement = jsonCharEscaper.apply(javaString.charAt(i));
            if (null != replacement) {
                if (i > runStart) {
                    stringBuilder.append(javaString, runStart, i);
                }
                stringBuilder.append(replacement);
                runStart = i + 1;
            }
        }
        if (runStart < len) {
            stringBuilder.append(javaString, runStart, len);
        }
        stringBuilder.append(QUOTE);
        return stringBuilder.toString();
    }

    private int indexOfFirstEscape(final String javaString, final int len) {
        for (int i = 0; i < len; i++) {
            if (null != jsonCharEscaper.apply(javaString.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

}
