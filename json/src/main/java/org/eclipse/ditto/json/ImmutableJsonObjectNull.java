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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of the NULL literal as JsonObject.
 */
@Immutable
final class ImmutableJsonObjectNull extends AbstractJsonValue implements JsonObject, JsonNull {

    @Nullable private static ImmutableJsonObjectNull instance = null;

    private ImmutableJsonObjectNull() {
        super();
    }

    /**
     * Returns an instance of {@code ImmutableJsonObjectNull}.
     *
     * @return the instance.
     */
    public static ImmutableJsonObjectNull getInstance() {
        ImmutableJsonObjectNull result = instance;
        if (null == result) {
            result = new ImmutableJsonObjectNull();
            instance = result;
        }
        return result;
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
    public ImmutableJsonObjectNull get(final JsonFieldDefinition<?> fieldDefinition) {
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

    @Override
    public String toString() {
        return JsonFactory.nullLiteral().toString();
    }

}
