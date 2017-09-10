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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * Immutable implementation of a JSON field definition with optional markers.
 */
@Immutable
final class ImmutableJsonFieldDefinition implements JsonFieldDefinition {

    private final JsonPointer pointer;
    private final Class<?> valueType;
    private final Set<JsonFieldMarker> markers;

    private ImmutableJsonFieldDefinition(final JsonPointer pointer, final Class<?> valueType,
            final Set<JsonFieldMarker> markers) {
        this.pointer = pointer;
        this.valueType = valueType;
        this.markers = Collections.unmodifiableSet(new HashSet<>(markers));
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldDefinition} with the specified pointer and value type but
     * without any markers.
     *
     * @param pointer the pointer which refers to the defined JSON field.
     * @param valueType the type of the value of the defined JSON field.
     * @return a new JSON field definition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableJsonFieldDefinition of(final JsonPointer pointer, final Class<?> valueType) {
        return of(pointer, valueType, Collections.emptySet());
    }

    /**
     * Returns a new instance of {@code ImmutableJsonFieldDefinition} with the specified pointer, value type and
     * markers.
     *
     * @param pointer the pointer which refers to the defined JSON field.
     * @param valueType the type of the value of the defined JSON field.
     * @param markers zero or n markers which add user defined semantics to the defined JSON field.
     * @return a new JSON field definition.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableJsonFieldDefinition of(final JsonPointer pointer, final Class<?> valueType,
            final Set<JsonFieldMarker> markers) {
        requireNonNull(pointer, "The JSON pointer of the field definition must not be null!");
        requireNonNull(valueType, "The value type of the field definition must not be null!");
        requireNonNull(markers, "The set of markers must not be null!");

        return new ImmutableJsonFieldDefinition(pointer, valueType, markers);
    }

    @Override
    public JsonPointer getPointer() {
        return pointer;
    }

    @Override
    public Class<?> getValueType() {
        return valueType;
    }

    @Override
    public Set<JsonFieldMarker> getMarkers() {
        return markers;
    }

    @Override
    public boolean isMarkedAs(final JsonFieldMarker fieldMarker, final JsonFieldMarker... furtherFieldMarkers) {
        requireNonNull(fieldMarker, "At least one marker has to be specified!");

        final Collection<JsonFieldMarker> askedMarkers =
                new HashSet<>(1 + (null != furtherFieldMarkers ? furtherFieldMarkers.length : 0));
        askedMarkers.add(fieldMarker);
        if (null != furtherFieldMarkers) {
            Collections.addAll(askedMarkers, furtherFieldMarkers);
        }
        return markers.containsAll(askedMarkers);
    }

    @SuppressWarnings({"checkstyle:com.puppycrawl.tools.checkstyle.checks.metrics.CyclomaticComplexityCheck",
            "squid:MethodCyclomaticComplexity"})
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableJsonFieldDefinition that = (ImmutableJsonFieldDefinition) o;
        return Objects.equals(pointer, that.pointer) && Objects.equals(valueType, that.valueType)
                && Objects.equals(markers, that.markers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pointer, valueType, markers);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "pointer=" + pointer + ", valueType=" + valueType + ", markers="
                + markers + "]";
    }

}
