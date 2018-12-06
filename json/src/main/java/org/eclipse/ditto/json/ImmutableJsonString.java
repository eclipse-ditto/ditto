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
        final JsonCharEscaper jsonStringEscaper = JsonCharEscaper.getInstance();
        return jsonStringEscaper.apply(c);
    }

}
