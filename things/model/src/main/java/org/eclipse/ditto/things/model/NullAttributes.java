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
package org.eclipse.ditto.things.model;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.SerializationContext;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * JSON NULL value version of {@link Attributes}.
 */
@Immutable
final class NullAttributes implements Attributes {

    private static final String NOT_A_NUMBER = "This JSON value is not a number: ";

    private final JsonObject wrapped;

    private NullAttributes() {
        wrapped = JsonFactory.nullObject();
    }

    /**
     * Creates a new {@code NullAttributes} object.
     *
     * @return the new NullAttributes object.
     */
    public static Attributes newInstance() {
        return new NullAttributes();
    }

    @Override
    public boolean isBoolean() {
        return false;
    }

    @Override
    public boolean isNumber() {
        return false;
    }

    @Override
    public boolean isInt() {
        return false;
    }

    @Override
    public boolean isLong() {
        return false;
    }

    @Override
    public boolean isDouble() {
        return false;
    }

    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public boolean asBoolean() {
        throw new UnsupportedOperationException("This JSON value is not a boolean: " + toString());
    }

    @Override
    public int asInt() {
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
    }

    @Override
    public long asLong() {
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
    }

    @Override
    public double asDouble() {
        throw new UnsupportedOperationException(NOT_A_NUMBER + toString());
    }

    @Override
    public String asString() {
        throw new UnsupportedOperationException("This JSON value is not a string: " + toString());
    }

    @Override
    public JsonObject asObject() {
        return this;
    }

    @Override
    public JsonArray asArray() {
        throw new UnsupportedOperationException("This JSON value is not an array: " + toString());
    }

    @Override
    public Attributes setValue(final CharSequence name, final int value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(name, value));
    }

    @Override
    public Attributes setValue(final CharSequence name, final long value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(name, value));
    }

    @Override
    public Attributes setValue(final CharSequence name, final double value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(name, value));
    }

    @Override
    public Attributes setValue(final CharSequence key, final boolean value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(key, value));
    }

    @Override
    public Attributes setValue(final CharSequence name, final String value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(name, value));
    }

    @Override
    public Attributes setValue(final CharSequence name, final JsonValue value) {
        return ImmutableAttributes.of(JsonFactory.newObject().setValue(name, value));
    }

    @Override
    public <T> Attributes set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return ImmutableAttributes.of(JsonFactory.newObject().set(fieldDefinition, value));
    }

    @Override
    public Attributes set(final JsonField field) {
        return ImmutableAttributes.of(JsonFactory.newObject().set(field));
    }

    @Override
    public Attributes setAll(final Iterable<JsonField> jsonFields) {
        return ImmutableAttributes.of(JsonFactory.newObject().setAll(jsonFields));
    }

    @Override
    public boolean contains(final CharSequence key) {
        return false;
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence name) {
        return Optional.empty();
    }

    @Override
    public JsonObject get(final JsonPointer pointer) {
        return this;
    }

    @Override
    public JsonObject get(final JsonFieldDefinition<?> fieldDefinition) {
        return this;
    }

    @Override
    public <T> Optional<T> getValue(final JsonFieldDefinition<T> fieldDefinition) {
        return Optional.empty();
    }

    @Override
    public <T> T getValueOrThrow(final JsonFieldDefinition<T> fieldDefinition) {
        throw new JsonMissingFieldException(fieldDefinition.getPointer());
    }

    @Override
    public JsonObject get(final JsonFieldSelector fieldSelector) {
        return this;
    }

    @Override
    public Attributes remove(final CharSequence key) {
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
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public Stream<JsonField> stream() {
        return Stream.empty();
    }

    @Override
    public JsonObject toJson() {
        return wrapped;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return wrapped;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return wrapped;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrapped);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NullAttributes other = (NullAttributes) o;
        return Objects.equals(wrapped, other.wrapped);
    }

    @Override
    public String toString() {
        return wrapped.toString();
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        JsonFactory.nullLiteral().writeValue(serializationContext);
    }

    @Override
    public long getUpperBoundForStringSize() {
        return JsonFactory.nullLiteral().getUpperBoundForStringSize();
    }
}
