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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

/**
 * An immutable implementation of {@link LogicalSearchFilter}.
 */
@Immutable
final class ImmutableLogicalFilter implements LogicalSearchFilter {

    private final Type type;
    private final Collection<SearchFilter> searchFilters;

    private ImmutableLogicalFilter(final Type theFilterType, final Collection<SearchFilter> theFilters) {
        type = theFilterType;
        searchFilters = Collections.unmodifiableList(new ArrayList<>(theFilters));
    }

    /**
     * Returns a new instance of {@code ImmutableLogicalFilter}
     *
     * @param filter the filter to be negated.
     * @return the new logical {@code NOT} filter.
     * @throws NullPointerException if {@code filter} is {@code null}.
     */
    public static ImmutableLogicalFilter not(final SearchFilter filter) {
        checkNotNull(filter, "filter to be negated");

        return new ImmutableLogicalFilter(Type.NOT, Collections.singleton(filter));
    }

    /**
     * Returns a new {@code ImmutableLogicalFilter} which concatenates the given filters with the boolean operator
     * {@code AND}.
     *
     * @param filter1 the first filter to be concatenated with at least {@code filter2} and optionally additionally with
     * {@code furtherFilters}.
     * @param filter2 the second filter to be concatenated with {@code filter1} and {@code furtherFilters}.
     * @param furtherFilters optional additional filters to be concatenated.
     * @return the new logical {@code AND} search filter.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableLogicalFilter and(final SearchFilter filter1, final SearchFilter filter2,
            final SearchFilter... furtherFilters) {
        checkNotNull(filter1, "first filter to be concatenated with AND");
        checkNotNull(filter2, "second filter to be concatenated with AND");
        checkNotNull(furtherFilters, "further searchFilters to be concatenated with AND");

        return new ImmutableLogicalFilter(Type.AND, paramsToList(filter1, filter2, furtherFilters));
    }

    /**
     * Returns a new {@code ImmutableLogicalFilter} which concatenates the given filters with the boolean operator
     * {@code OR}.
     *
     * @param filter1 the first filter to be concatenated with at least {@code filter2} and optionally additionally with
     * {@code furtherFilters}.
     * @param filter2 the second filter to be concatenated with {@code filter1} and {@code furtherFilters}.
     * @param furtherFilters optional additional filters to be concatenated.
     * @return the new logical {@code OR} search filter.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImmutableLogicalFilter or(final SearchFilter filter1, final SearchFilter filter2,
            final SearchFilter... furtherFilters) {
        checkNotNull(filter1, "first filter to be concatenated with OR");
        checkNotNull(filter2, "second filter to be concatenated with OR");
        checkNotNull(furtherFilters, "further searchFilters to be concatenated with OR");

        return new ImmutableLogicalFilter(Type.OR, paramsToList(filter1, filter2, furtherFilters));
    }

    @SafeVarargs
    private static <T> List<T> paramsToList(final T param1, final T param2, final T... furtherParams) {
        final List<T> result = new ArrayList<>(2 + furtherParams.length);
        result.add(param1);
        result.add(param2);
        Collections.addAll(result, furtherParams);
        return result;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Collection<SearchFilter> getFilters() {
        return searchFilters;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableLogicalFilter that = (ImmutableLogicalFilter) o;
        return type == that.type && Objects.equals(searchFilters, that.searchFilters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, searchFilters);
    }

    @Override
    public String toString() {
        return createFilterString();
    }

    private String createFilterString() {
        final String delimiter = ",";
        final String prefix = type.getName() + "(";
        final String suffix = ")";

        return searchFilters.stream()
                .map(SearchFilter::toString)
                .collect(Collectors.joining(delimiter, prefix, suffix));
    }

}
