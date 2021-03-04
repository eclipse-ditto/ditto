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

import java.io.IOException;
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

    private static final long MAX_CHAR_ESCAPE_SEQUENCE_LENGTH = 6; // "\u1234"
    private static final long NUM_ENCLOSING_QUOTES = 2;

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
        return value.hashCode();
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

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        serializationContext.writeString(value);
    }

    @Override
    public long getUpperBoundForStringSize() {
        if (stringRepresentation != null) {
            return stringRepresentation.length();
        }
        return (value.length() * MAX_CHAR_ESCAPE_SEQUENCE_LENGTH) + NUM_ENCLOSING_QUOTES;
    }

    private String createStringRepresentation() {
        final JavaStringToEscapedJsonString javaStringToEscapedJsonString = JavaStringToEscapedJsonString.getInstance();
        return javaStringToEscapedJsonString.apply(value);
    }

}
