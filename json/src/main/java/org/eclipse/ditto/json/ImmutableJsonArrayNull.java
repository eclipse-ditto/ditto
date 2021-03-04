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

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of the NULL literal as JsonArray.
 */
@Immutable
final class ImmutableJsonArrayNull extends AbstractJsonValue implements JsonArray, JsonNull {

    @Nullable private static ImmutableJsonArrayNull instance = null;

    private ImmutableJsonArrayNull() {
        super();
    }

    /**
     * Returns an instance of {@code ImmutableJsonArrayNull}.
     *
     * @return the instance.
     */
    public static ImmutableJsonArrayNull getInstance() {
        ImmutableJsonArrayNull result = instance;
        if (null == result) {
            result = new ImmutableJsonArrayNull();
            instance = result;
        }
        return result;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public JsonArray asArray() {
        return this;
    }

    @Override
    public JsonArray add(final int value, final int... furtherValues) {
        return this;
    }

    @Override
    public JsonArray add(final long value, final long... furtherValues) {
        return this;
    }

    @Override
    public JsonArray add(final double value, final double... furtherValues) {
        return this;
    }

    @Override
    public JsonArray add(final boolean value, final boolean... furtherValues) {
        return this;
    }

    @Override
    public JsonArray add(final String value, final String... furtherValues) {
        return this;
    }

    @Override
    public JsonArray add(final JsonValue value, final JsonValue... furtherValues) {
        return this;
    }

    @Override
    public Optional<JsonValue> get(final int index) {
        return Optional.empty();
    }

    @Override
    public boolean contains(final JsonValue value) {
        return false;
    }

    @Override
    public int indexOf(final JsonValue value) {
        return 0;
    }

    @Override
    public Stream<JsonValue> stream() {
        return Stream.empty();
    }

    @Override
    public Iterator<JsonValue> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof JsonNull;
    }

    @Override
    public int hashCode() {
        return JsonNull.class.hashCode();
    }

    @Override
    public String toString() {
        return JsonFactory.nullLiteral().toString();
    }

}
