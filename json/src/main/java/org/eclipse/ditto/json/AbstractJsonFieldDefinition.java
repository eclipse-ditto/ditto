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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * This abstract implementation of {@link JsonFieldDefinition} is meant to be used for all value
 * types which are supported by {@link JsonValue} to make parsing a JsonObject easier and most
 * notably, type safe.
 *
 * @param <T> the type of this definition's value type.
 */
@Immutable
abstract class AbstractJsonFieldDefinition<T> implements JsonFieldDefinition<T> {

    private final JsonPointer pointer;
    private final Class<T> valueType;
    private final Function<JsonValue, Boolean> checkJavaTypeFunction;
    private final Function<JsonValue, T> mappingFunction;
    private final Set<JsonFieldMarker> markers;

    /**
     * Constructs a new {@code AbstractJsonFieldDefinition} object.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param valueType the type of the value of the defined JSON field.
     * @param checkJavaTypeFunction the function which checks if a given JsonValue represents the expected Java type.
     * @param mappingFunction the function for converting a JsonValue into the value type.
     * @param markers optional markers which add user defined semantics to the defined JSON field.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    protected AbstractJsonFieldDefinition(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, Boolean> checkJavaTypeFunction,
            final Function<JsonValue, T> mappingFunction,
            final JsonFieldMarker... markers) {

        requireNonNull(pointer, "The JSON pointer of the field definition must not be null!");
        requireNonNull(markers, "The markers must not be null!");

        this.pointer = JsonFactory.newPointer(pointer);
        this.valueType = requireNonNull(valueType, "The value type of the field definition must not be null!");
        this.checkJavaTypeFunction =
                requireNonNull(checkJavaTypeFunction, "The Java type checking function must not be null!");
        this.mappingFunction = requireNonNull(mappingFunction, "The mapping function must not be null!");

        final Set<JsonFieldMarker> mutableMarkersSet = new HashSet<>(markers.length);
        Collections.addAll(mutableMarkersSet, markers);

        this.markers = Collections.unmodifiableSet(mutableMarkersSet);
    }

    @Override
    public JsonPointer getPointer() {
        return pointer;
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }

    @Override
    public Set<JsonFieldMarker> getMarkers() {
        return markers;
    }

    @Override
    public boolean isMarkedAs(final JsonFieldMarker fieldMarker, final JsonFieldMarker... furtherFieldMarkers) {
        requireNonNull(fieldMarker, "At least one marker has to be specified!");
        requireNonNull(furtherFieldMarkers, "The further field markers must not be null!");

        final Collection<JsonFieldMarker> askedMarkers = new HashSet<>(1 + furtherFieldMarkers.length);
        askedMarkers.add(fieldMarker);
        Collections.addAll(askedMarkers, furtherFieldMarkers);

        return markers.containsAll(askedMarkers);
    }

    @Nullable
    @Override
    public T mapValue(final JsonValue jsonValue) {
        checkValueType(jsonValue);
        return getAsJavaType(jsonValue, mappingFunction);
    }

    private void checkValueType(final JsonValue jsonValue) {
        requireNonNull(jsonValue, "The JsonValue to be mapped must not be (Java) null!");
        if (!hasExpectedValueType(jsonValue)) {
            final String msgPattern = "Value <{0}> for <{1}> is not of type <{2}>!";
            final String message = MessageFormat.format(msgPattern, jsonValue, pointer, valueType.getSimpleName());
            throw new JsonParseException(message);
        }
    }

    private boolean hasExpectedValueType(final JsonValue jsonValue) {
        return jsonValue.isNull() || checkJavaTypeFunction.apply(jsonValue);
    }

    @Nullable
    protected abstract T getAsJavaType(JsonValue jsonValue, Function<JsonValue, T> mappingFunction);

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractJsonFieldDefinition<?> that = (AbstractJsonFieldDefinition<?>) o;
        return Objects.equals(pointer, that.pointer) &&
                Objects.equals(valueType, that.valueType) &&
                Objects.equals(checkJavaTypeFunction, that.checkJavaTypeFunction) &&
                Objects.equals(mappingFunction, that.mappingFunction) &&
                Objects.equals(markers, that.markers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointer, valueType, checkJavaTypeFunction, mappingFunction, markers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "pointer=" + pointer +
                ", valueType=" + valueType +
                ", checkJavaTypeFunction=" + checkJavaTypeFunction +
                ", mappingFunction=" + mappingFunction +
                ", markers=" + markers +
                "]";
    }

}
