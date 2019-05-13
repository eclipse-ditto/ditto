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

import java.util.function.Function;
import java.util.function.UnaryOperator;

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

    private final Function<Integer, String> jsonCharEscaper;

    private JavaStringToEscapedJsonString(final Function<Integer, String> theJsonCharEscaper) {
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
        javaString.chars().forEachOrdered(codePoint -> stringBuilder.append(jsonCharEscaper.apply(codePoint)));
        stringBuilder.append(QUOTE);
        return stringBuilder.toString();
    }

}
