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
 * This implementation of {@link JsonFieldDefinition} is meant to be used for all value types which are supported by
 * {@link JsonValue} to make parsing a JsonObject easier and most notably type safe.
 *
 * @param <T> the type of this definition's value type.
 */
@Immutable
final class ImmutableJsonFieldDefinition<T> implements JsonFieldDefinition<T> {

    private final JsonPointer pointer;
    private final Class<T> valueType;
    private final Set<JsonFieldMarker> markers;
    private final Function<JsonValue, T> mappingFunction;

    private ImmutableJsonFieldDefinition(final JsonPointer thePointer,
            final Class<T> theValueType,
            final Function<JsonValue, T> theMappingFunction,
            final Set<JsonFieldMarker> theMarkers) {

        pointer = thePointer;
        valueType = theValueType;
        mappingFunction = theMappingFunction;
        markers = Collections.unmodifiableSet(new HashSet<>(theMarkers));
    }

    /**
     * Returns a new instance of {@code TypeSafeJsonFieldDefinition}.
     *
     * @param pointer a character sequence consisting of either a single JSON key or a slash delimited hierarchy of JSON
     * keys aka JSON pointer.
     * @param valueType the type of the value of the defined JSON field.
     * @param mappingFunction the function for converting a JsonValue into the value type.
     * @param markers optional markers which add user defined semantics to the defined JSON field.
     * @param <T> the type of value type.
     * @return the instance.
     * @throws NullPointerException if any argument but {@code markers} is {@code null}.
     * @throws IllegalArgumentException if {@code pointer} is empty.
     */
    public static <T> ImmutableJsonFieldDefinition<T> newInstance(final CharSequence pointer,
            final Class<T> valueType,
            final Function<JsonValue, T> mappingFunction,
            @Nullable final JsonFieldMarker ... markers) {

        requireNonNull(pointer, "The JSON pointer of the field definition must not be null!");
        requireNonNull(valueType, "The value type of the field definition must not be null!");
        requireNonNull(mappingFunction, "The mapping function must not be null!");
        requireNonNull(markers, "The markers must not be null!");

        final Set<JsonFieldMarker> markersSet = new HashSet<>(markers.length);
        Collections.addAll(markersSet, markers);

        return new ImmutableJsonFieldDefinition<>(JsonFactory.newPointer(pointer), valueType, mappingFunction,
                markersSet);
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

    @Override
    public T mapValue(final JsonValue jsonValue) {
        checkValueType(jsonValue);
        return getAsExpectedValueType(jsonValue);
    }

    private void checkValueType(final JsonValue jsonValue) {
        requireNonNull(jsonValue, "The JsonValue to be mapped must not be null!");
        if (!hasExpectedValueType(jsonValue)) {
            final String msgPattern = "Value <{0}> for <{1}> is not of type <{2}>!";
            final String message = MessageFormat.format(msgPattern, jsonValue, pointer, getValueType().getSimpleName());
            throw new JsonParseException(message);
        }
    }

    private boolean hasExpectedValueType(final JsonValue jsonValue) {
        return jsonValue.isRepresentationOfJavaType(getValueType());
    }

    private T getAsExpectedValueType(final JsonValue jsonValue) {
        return mappingFunction.apply(jsonValue);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonFieldDefinition<?> that = (ImmutableJsonFieldDefinition<?>) o;
        return Objects.equals(pointer, that.pointer) &&
                Objects.equals(valueType, that.valueType) &&
                Objects.equals(markers, that.markers) &&
                Objects.equals(mappingFunction, that.mappingFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointer, valueType, markers, mappingFunction);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "pointer=" + pointer +
                ", valueType=" + valueType +
                ", markers=" + markers +
                ", mappingFunction=" + mappingFunction +
                "]";
    }

}
