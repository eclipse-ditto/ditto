/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.model;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.SerializationContext;

/**
 * A package internal helper construct to extend {@link JsonObject} with an additional type information.
 *
 * @param <T> the type to preserve when determining results.
 */
interface TypedJsonObject<T extends JsonObject> extends JsonObject {

    /**
     * Determines the result by receiving the {@link JsonObject} from the passed supplier and preserving the type
     * {@code T} in the response.
     *
     * @param newWrappedSupplier the supplier supplying the JsonObject to determine the result from.
     * @return the instance of type {@code T}.
     */
    T determineResult(Supplier<JsonObject> newWrappedSupplier);

    /**
     * @return the wrapped, underlying JsonObject.
     */
    JsonObject getWrappedObject();

    @Override
    default T setValue(final CharSequence key, final int value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    default T setValue(final CharSequence key, final long value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    default T setValue(final CharSequence key, final double value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    default T setValue(final CharSequence key, final boolean value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    default T setValue(final CharSequence key, final String value) {
        return setValue(key, JsonFactory.newValue(value));
    }

    @Override
    default T setValue(final CharSequence key, final JsonValue value) {
        return determineResult(() -> getWrappedObject().setValue(key, value));
    }

    @Override
    default <J> T set(final JsonFieldDefinition<J> fieldDefinition, @Nullable final J value) {
        return determineResult(() -> getWrappedObject().set(fieldDefinition, value));
    }

    @Override
    default T set(final JsonField field) {
        return determineResult(() -> getWrappedObject().set(field));
    }

    @Override
    default T setAll(final Iterable<JsonField> jsonFields) {
        return determineResult(() -> getWrappedObject().setAll(jsonFields));
    }

    @Override
    default boolean contains(final CharSequence key) {
        return getWrappedObject().contains(key);
    }

    @Override
    default JsonObject get(final JsonPointer pointer) {
        return getWrappedObject().get(pointer);
    }

    @Override
    default JsonObject get(final JsonFieldDefinition<?> fieldDefinition) {
        return getWrappedObject().get(fieldDefinition);
    }

    @Override
    default JsonObject get(final JsonFieldSelector fieldSelector) {
        return getWrappedObject().get(fieldSelector);
    }

    @Override
    default Optional<JsonValue> getValue(final CharSequence key) {
        return getWrappedObject().getValue(key);
    }

    @Override
    default <J> Optional<J> getValue(final JsonFieldDefinition<J> fieldDefinition) {
        return getWrappedObject().getValue(fieldDefinition);
    }

    @Override
    default <J> J getValueOrThrow(final JsonFieldDefinition<J> fieldDefinition) {
        return getWrappedObject().getValueOrThrow(fieldDefinition);
    }

    @Override
    default T remove(final CharSequence key) {
        return determineResult(() -> getWrappedObject().remove(key));
    }

    @Override
    default List<JsonKey> getKeys() {
        return getWrappedObject().getKeys();
    }

    @Override
    default Optional<JsonField> getField(final CharSequence key) {
        return getWrappedObject().getField(key);
    }

    @Override
    default boolean isBoolean() {
        return getWrappedObject().isBoolean();
    }

    @Override
    default boolean isNumber() {
        return getWrappedObject().isNumber();
    }

    @Override
    default boolean isInt() {
        return getWrappedObject().isInt();
    }

    @Override
    default boolean isLong() {
        return getWrappedObject().isLong();
    }

    @Override
    default boolean isDouble() {
        return getWrappedObject().isDouble();
    }

    @Override
    default boolean isString() {
        return getWrappedObject().isString();
    }

    @Override
    default boolean isObject() {
        return getWrappedObject().isObject();
    }

    @Override
    default boolean isArray() {
        return getWrappedObject().isArray();
    }

    @Override
    default boolean isNull() {
        return getWrappedObject().isNull();
    }

    @Override
    default boolean asBoolean() {
        return getWrappedObject().asBoolean();
    }

    @Override
    default int asInt() {
        return getWrappedObject().asInt();
    }

    @Override
    default long asLong() {
        return getWrappedObject().asLong();
    }

    @Override
    default double asDouble() {
        return getWrappedObject().asDouble();
    }

    @Override
    default String asString() {
        return getWrappedObject().asString();
    }

    @Override
    default JsonObject asObject() {
        return getWrappedObject().asObject();
    }

    @Override
    default JsonArray asArray() {
        return getWrappedObject().asArray();
    }

    @Override
    default void writeValue(final SerializationContext serializationContext) throws IOException {
        getWrappedObject().writeValue(serializationContext);
    }

    @Override
    default long getUpperBoundForStringSize() {
        return getWrappedObject().getUpperBoundForStringSize();
    }

    @Override
    default boolean isEmpty() {
        return getWrappedObject().isEmpty();
    }

    @Override
    default int getSize() {
        return getWrappedObject().getSize();
    }

    @Override
    default Stream<JsonField> stream() {
        return getWrappedObject().stream();
    }

    @Override
    default Iterator<JsonField> iterator() {
        return getWrappedObject().iterator();
    }

}
