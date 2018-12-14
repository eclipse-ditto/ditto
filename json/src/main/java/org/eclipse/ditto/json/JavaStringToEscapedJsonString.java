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

import java.util.function.Function;
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

    private static final char QUOTE = '\"';

    private final Function<Character, String> jsonCharEscaper;

    private JavaStringToEscapedJsonString(final Function<Character, String> theJsonCharEscaper) {
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
        final StringBuilder stringBuilder = new StringBuilder(javaString.length() + 2);
        stringBuilder.append(QUOTE);
        stringBuilder.append(javaString);
        int i = 1; // offset of starting " char
        for (final char c : javaString.toCharArray()) {
            @Nullable final String replacement = jsonCharEscaper.apply(c);
            if (null != replacement) {
                stringBuilder.replace(i, i + 1, replacement);
                i += replacement.length();
            } else {
                i++;
            }
        }
        stringBuilder.append(QUOTE);
        return stringBuilder.toString();
    }

}
