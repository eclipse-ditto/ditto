/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of the NULL literal as JsonArray.
 */
@Immutable
final class ImmutableJsonArrayNull extends AbstractImmutableJsonValue implements JsonArray, JsonNull {

    private ImmutableJsonArrayNull() {
        super();
    }

    /**
     * Creates a new {@code ImmutableJsonArrayNull} object.
     *
     * @return a new ImmutableJsonArrayNull object.
     */
    public static ImmutableJsonArrayNull newInstance() {
        return new ImmutableJsonArrayNull();
    }

    @Override
    protected String createStringRepresentation() {
        return JsonFactory.nullLiteral().toString();
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

}
