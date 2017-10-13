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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of the NULL literal as JsonObject.
 */
@Immutable
final class ImmutableJsonObjectNull extends AbstractImmutableJsonValue implements JsonObject, JsonNull {

    private ImmutableJsonObjectNull() {
        super();
    }

    /**
     * Creates a new {@code ImmutableJsonObjectNull} object.
     *
     * @return a new ImmutableJsonObjectNull object.
     */
    public static ImmutableJsonObjectNull newInstance() {
        return new ImmutableJsonObjectNull();
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
    public boolean isObject() {
        return true;
    }

    @Override
    public ImmutableJsonObjectNull asObject() {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final int value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final long value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final double value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final boolean value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final String value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setValue(final CharSequence key, final JsonValue value) {
        return this;
    }

    @Override
    public <T> ImmutableJsonObjectNull set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull set(final JsonField field) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull setAll(final Iterable<JsonField> jsonFields) {
        return this;
    }

    @Override
    public boolean contains(final CharSequence key) {
        return false;
    }

    @Override
    public ImmutableJsonObjectNull get(final JsonPointer pointer) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull get(final JsonFieldDefinition fieldDefinition) {
        return this;
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence key) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(final JsonFieldDefinition<T> fieldDefinition) {
        return Optional.empty();
    }

    @Override
    public <T> T getValueOrThrow(final JsonFieldDefinition<T> fieldDefinition) {
        throw JsonMissingFieldException.newBuilder().fieldName(fieldDefinition.getPointer()).build();
    }

    @Override
    public ImmutableJsonObjectNull get(final JsonFieldSelector fieldSelector) {
        return this;
    }

    @Override
    public ImmutableJsonObjectNull remove(final CharSequence key) {
        return this;
    }

    @Override
    public List<JsonKey> getKeys() {
        return Collections.emptyList();
    }

    @Override
    public Optional<JsonField> getField(final CharSequence key) {
        return Optional.empty();
    }

    @Override
    public Iterator<JsonField> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Stream<JsonField> stream() {
        return Stream.empty();
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
