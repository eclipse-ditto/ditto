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
package org.eclipse.ditto.thingsearch.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link SearchProperty}.
 */
@Immutable
final class ImmutableSearchProperty implements SearchProperty {

    private final JsonPointer propertyPath;

    private ImmutableSearchProperty(final JsonPointer thePropertyPath) {
        propertyPath = thePropertyPath;
    }

    /**
     * Returns a new instance of {@code ImmutableSearchProperty}.
     *
     * @param propertyPath the path of the property to be searched for.
     * @return the new search property.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static ImmutableSearchProperty of(final JsonPointer propertyPath) {
        checkNotNull(propertyPath, "property path");

        return new ImmutableSearchProperty(propertyPath);
    }

    @SafeVarargs
    private static <T> Collection<JsonValue> toCollection(final Function<T, JsonValue> toJsonValueFunction,
            final T value, final T... furtherValues) {
        checkNotNull(furtherValues, "additional values");

        final Collection<JsonValue> result = new ArrayList<>(1 + furtherValues.length);
        result.add(toJsonValueFunction.apply(value));
        for (final T furtherValue : furtherValues) {
            result.add(toJsonValueFunction.apply(furtherValue));
        }

        return result;
    }

    private static void checkStringValue(final String value) {
        checkNotNull(value, "string value");
    }

    @Override
    public PropertySearchFilter exists() {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EXISTS, propertyPath, Collections.emptySet());
    }

    @Override
    public PropertySearchFilter eq(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EQ, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter eq(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EQ, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter eq(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EQ, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter eq(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EQ, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter eq(@Nullable final String value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.EQ, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ne(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.NE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ne(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.NE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ne(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.NE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ne(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.NE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ne(@Nullable final String value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.NE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ge(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ge(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ge(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ge(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ge(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.GE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter gt(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter gt(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter gt(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter gt(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.GT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter gt(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.GT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter le(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter le(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter le(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter le(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter le(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.LE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter lt(final boolean value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter lt(final int value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter lt(final long value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter lt(final double value) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.LT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter lt(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.LT, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter like(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.LIKE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter ilike(final String value) {
        checkStringValue(value);

        return ImmutablePropertyFilter.of(SearchFilter.Type.ILIKE, propertyPath, JsonFactory.newValue(value));
    }

    @Override
    public PropertySearchFilter in(final boolean value, final Boolean... furtherValues) {
        return in(toCollection(JsonFactory::newValue, value, furtherValues));
    }

    @Override
    public PropertySearchFilter in(final int value, final Integer... furtherValues) {
        return in(toCollection(JsonFactory::newValue, value, furtherValues));
    }

    @Override
    public PropertySearchFilter in(final long value, final Long... furtherValues) {
        return in(toCollection(JsonFactory::newValue, value, furtherValues));
    }

    @Override
    public PropertySearchFilter in(final double value, final Double... furtherValues) {
        return in(toCollection(JsonFactory::newValue, value, furtherValues));
    }

    @Override
    public PropertySearchFilter in(final String value, final String... furtherValues) {
        checkStringValue(value);

        return in(toCollection(JsonFactory::newValue, value, furtherValues));
    }

    @Override
    public PropertySearchFilter in(final Collection<JsonValue> values) {
        return ImmutablePropertyFilter.of(SearchFilter.Type.IN, propertyPath, values);
    }

    @Override
    public JsonPointer getPath() {
        return propertyPath;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableSearchProperty immutableSearchProperty = (ImmutableSearchProperty) o;
        return Objects.equals(propertyPath, immutableSearchProperty.propertyPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyPath);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "propertyPath=" + propertyPath + "]";
    }

}
