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
import java.util.StringJoiner;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * An immutable implementation of {@link PropertySearchFilter}.
 */
@Immutable
final class ImmutablePropertyFilter implements PropertySearchFilter {

    private final Type type;
    private final JsonPointer propertyPath;
    private final Collection<JsonValue> values;

    private ImmutablePropertyFilter(final Type type, final CharSequence propertyPath,
            final Collection<JsonValue> values) {

        this.type = checkNotNull(type, "type");
        this.propertyPath = JsonFactory.newPointer(checkNotNull(propertyPath, "propertyPath"));
        this.values = Collections.unmodifiableList(new ArrayList<>(values));
    }

    /**
     * Returns an instance of {@code ImmutablePropertyFilter}.
     *
     * @param type the type of the returned filter.
     * @param propertyPath the path of the property of the filter.
     * @param filterValues the values the actual property value is to matched with.
     * @return the property search filter with the specified type, property path and not values.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutablePropertyFilter of(final Type type, final CharSequence propertyPath,
            final Collection<JsonValue> filterValues) {

        return new ImmutablePropertyFilter(type, propertyPath, checkNotNull(filterValues, "filter values"));
    }

    /**
     * Returns an instance of {@code ImmutablePropertyFilter}.
     *
     * @param type the type of the returned filter.
     * @param propertyPath the path of the property of the filter.
     * @param filterValue the mandatory value the actual property value is to matched with.
     * @param furtherFilterValues additional optional values the actual value of the property is to be matched with.
     * @return the property search filter with the specified type, property path and not values.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutablePropertyFilter of(final Type type,
            final CharSequence propertyPath,
            final JsonValue filterValue,
            final JsonValue... furtherFilterValues) {

        checkNotNull(filterValue, "filter values");
        checkNotNull(furtherFilterValues, "further filter values");

        final Collection<JsonValue> filterValues = new ArrayList<>(1 + furtherFilterValues.length);
        filterValues.add(filterValue);
        Collections.addAll(filterValues, furtherFilterValues);

        return of(type, propertyPath, filterValues);
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public JsonPointer getPath() {
        return propertyPath;
    }

    @Override
    public Collection<JsonValue> getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutablePropertyFilter that = (ImmutablePropertyFilter) o;
        return type == that.type && Objects.equals(propertyPath, that.propertyPath)
                && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, propertyPath, values);
    }

    @Override
    public String toString() {
        return createFilterString();
    }

    private String createFilterString() {
        final String delimiter = ",";
        final String prefix = type.getName() + "(";
        final String suffix = ")";

        final StringJoiner stringJoiner = new StringJoiner(delimiter, prefix, suffix);
        stringJoiner.add(propertyPath.toString());
        values.stream()
                .map(JsonValue::toString)
                .forEach(stringJoiner::add);

        return stringJoiner.toString();
    }

}
