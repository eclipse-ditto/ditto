/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.metadata;

import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.SerializationContext;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link org.eclipse.ditto.base.model.entity.metadata.Metadata}.
 */
@Immutable
final class ImmutableMetadata implements Metadata {

    private final JsonObject wrapped;

    private ImmutableMetadata(final JsonObject toWrap) {
        wrapped = toWrap;
    }

    /**
     * Returns a new empty instance of {@code ImmutableAttributes}.
     *
     * @return the new empty attributes.
     */
    public static Metadata empty() {
        return new ImmutableMetadata(JsonFactory.newObject());
    }

    /**
     * Returns a new instance of {@code ImmutableMetadata} which is initialized with the data of the given JSON
     * object.
     *
     * @param jsonObject provides the data to initialize the new metadata with.
     * @return the new metadata.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static Metadata of(final JsonObject jsonObject) {
        if (jsonObject instanceof ImmutableMetadata) {
            return (Metadata) jsonObject;
        }
        return new ImmutableMetadata(checkNotNull(jsonObject, "jsonObject"));
    }

    @Override
    public boolean isBoolean() {
        return wrapped.isBoolean();
    }

    @Override
    public boolean isNumber() {
        return wrapped.isNumber();
    }

    @Override
    public boolean isInt() {
        return wrapped.isInt();
    }

    @Override
    public boolean isLong() {
        return wrapped.isLong();
    }

    @Override
    public boolean isDouble() {
        return wrapped.isDouble();
    }

    @Override
    public boolean isString() {
        return wrapped.isString();
    }

    @Override
    public boolean isObject() {
        return wrapped.isObject();
    }

    @Override
    public boolean isArray() {
        return wrapped.isArray();
    }

    @Override
    public boolean isNull() {
        return wrapped.isNull();
    }

    @Override
    public boolean asBoolean() {
        return wrapped.asBoolean();
    }

    @Override
    public int asInt() {
        return wrapped.asInt();
    }

    @Override
    public long asLong() {
        return wrapped.asLong();
    }

    @Override
    public double asDouble() {
        return wrapped.asDouble();
    }

    @Override
    public String asString() {
        return wrapped.asString();
    }

    @Override
    public JsonObject asObject() {
        return wrapped.asObject();
    }

    @Override
    public JsonArray asArray() {
        return wrapped.asArray();
    }

    @Override
    public Metadata setValue(final CharSequence key, final int value) {
        return setValue(key, newValue(value));
    }

    @Override
    public Metadata setValue(final CharSequence key, final long value) {
        return setValue(key, newValue(value));
    }

    @Override
    public Metadata setValue(final CharSequence key, final double value) {
        return setValue(key, newValue(value));
    }

    @Override
    public Metadata setValue(final CharSequence key, final boolean value) {
        return setValue(key, newValue(value));
    }

    @Override
    public Metadata setValue(final CharSequence key, final String value) {
        return setValue(key, newValue(value));
    }

    @Override
    public Metadata setValue(final CharSequence key, final JsonValue value) {
        return determineResult(() -> wrapped.setValue(key, value));
    }

    @Override
    public <T> Metadata set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return determineResult(() -> wrapped.set(fieldDefinition, value));
    }

    @Override
    public Metadata set(final JsonField field) {
        return determineResult(() -> wrapped.set(field));
    }

    @Override
    public Metadata setAll(final Iterable<JsonField> jsonFields) {
        return determineResult(() -> wrapped.setAll(jsonFields));
    }

    @Override
    public Metadata remove(final CharSequence key) {
        return determineResult(() -> wrapped.remove(key));
    }

    @Override
    public boolean contains(final CharSequence key) {
        return wrapped.contains(key);
    }

    @Override
    public JsonObject get(final JsonPointer pointer) {
        return wrapped.get(pointer);
    }

    @Override
    public JsonObject get(final JsonFieldDefinition<?> fieldDefinition) {
        return wrapped.get(fieldDefinition);
    }

    @Override
    public <T> Optional<T> getValue(final JsonFieldDefinition<T> fieldDefinition) {
        return wrapped.getValue(fieldDefinition);
    }

    @Override
    public <T> T getValueOrThrow(final JsonFieldDefinition<T> fieldDefinition) {
        return wrapped.getValueOrThrow(fieldDefinition);
    }

    @Override
    public JsonObject get(final JsonFieldSelector fieldSelector) {
        return wrapped.get(fieldSelector);
    }

    @Override
    public Optional<JsonValue> getValue(final CharSequence key) {
        return wrapped.getValue(key);
    }

    @Override
    public List<JsonKey> getKeys() {
        return wrapped.getKeys();
    }

    @Override
    public Optional<JsonField> getField(final CharSequence key) {
        return wrapped.getField(key);
    }

    @Override
    public boolean isEmpty() {
        return wrapped.isEmpty();
    }

    @Override
    public int getSize() {
        return wrapped.getSize();
    }

    @Override
    public Iterator<JsonField> iterator() {
        return wrapped.iterator();
    }

    @Override
    public Stream<JsonField> stream() {
        return wrapped.stream();
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S1067"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableMetadata that = (ImmutableMetadata) o;
        return Objects.equals(wrapped, that.wrapped);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wrapped);
    }

    @Override
    public String toString() {
        return wrapped.toString();
    }

    @Override
    public void writeValue(final SerializationContext serializationContext) throws IOException {
        wrapped.writeValue(serializationContext);
    }

    @Override
    public long getUpperBoundForStringSize() {
        return wrapped.getUpperBoundForStringSize();
    }

    private Metadata determineResult(final Supplier<JsonObject> newWrappedSupplier) {
        final JsonObject newWrapped = newWrappedSupplier.get();
        if (!newWrapped.equals(wrapped)) {
            return of(newWrapped);
        }
        return this;
    }

    @Override
    public JsonObject toJson() {
        return this;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return stream()
            .filter(field -> !field.getDefinition().isPresent() || predicate.test(field))
            .collect(JsonCollectors.fieldsToObject());
    }
}
