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
 * A null implementation of {@link FeatureProperties}.
 */
@Immutable
final class NullFeatureProperties implements FeatureProperties {

    private final JsonObject wrapped;

    private NullFeatureProperties() {
        wrapped = JsonFactory.nullObject();
    }

    /**
     * Creates new Properties with JSON NULL value.
     *
     * @return the new Properties.
     */
    public static FeatureProperties newInstance() {
        return new NullFeatureProperties();
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
        return this;
    }

    @Override
    public JsonArray asArray() {
        return wrapped.asArray();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final int value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final long value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final double value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final boolean value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final String value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public FeatureProperties setValue(final CharSequence key, final JsonValue value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(key, value)
                .build();
    }

    @Override
    public <T> JsonObject set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(fieldDefinition, value)
                .build();
    }

    @Override
    public FeatureProperties set(final JsonField field) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .set(field)
                .build();
    }

    @Override
    public FeatureProperties setAll(final Iterable<JsonField> jsonFields) {
        return ThingsModelFactory.newFeaturePropertiesBuilder()
                .setAll(jsonFields)
                .build();
    }

    @Override
    public boolean contains(final CharSequence key) {
        return false;
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
    public Optional<JsonValue> getValue(final CharSequence name) {
        return Optional.empty();
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
    public FeatureProperties remove(final CharSequence key) {
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
        final NullFeatureProperties other = (NullFeatureProperties) o;
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
