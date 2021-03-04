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

import java.io.IOException;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable JSON boolean value.
 */
@Immutable
final class ImmutableJsonBoolean extends AbstractJsonValue {

    /**
     * The JSON literal for the boolean value {@code true}.
     */
    static final ImmutableJsonBoolean TRUE = ImmutableJsonBoolean.of(true);

    /**
     * The JSON literal for the boolean value {@code false}.
     */
    static final ImmutableJsonBoolean FALSE = ImmutableJsonBoolean.of(false);

    private final boolean value;

    private ImmutableJsonBoolean(final boolean theValue) {
        value = theValue;
    }

    /**
     * Creates a new {@code ImmutableJsonLiteral} object of a Minimal Json Literal.
     *
     * @return a new ImmutableJsonLiteral object.
     */
    public static ImmutableJsonBoolean of(final boolean value) {
        return new ImmutableJsonBoolean(value);
    }

    @Override
    public boolean isBoolean() {
        return true;
    }

    @Override
    public boolean asBoolean() {
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
        final ImmutableJsonBoolean that = (ImmutableJsonBoolean) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        serializationContext.writeBoolean(value);
    }

    @Override
    public long getUpperBoundForStringSize() {
        return 5;
    }
}
