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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;

/**
 * A mutable builder with a fluent API for an immutable {@link SearchQuery}.
 */
@NotThreadSafe
final class ImmutableSearchQueryBuilder implements SearchQueryBuilder {

    private final SearchFilter searchFilter;
    private final Map<JsonPointer, SortOptionEntry> sortOptionEntries;
    private LimitOption limitOption;

    private ImmutableSearchQueryBuilder(final SearchFilter theSearchFilter) {
        searchFilter = theSearchFilter;
        sortOptionEntries = new LinkedHashMap<>();
        limitOption = null;
    }

    /**
     * Returns a new instance of {@code ImmutableSearchQueryBuilder} with the given search filter.
     *
     * @param searchFilter the search filter which is the crucial part of the search query.
     * @return the new search query builder.
     * @throws NullPointerException if {@code searchFilter} is {@code null}.
     */
    public static ImmutableSearchQueryBuilder of(final SearchFilter searchFilter) {
        return new ImmutableSearchQueryBuilder(checkNotNull(searchFilter, "search filter"));
    }

    @Override
    public SearchQueryBuilder sortAsc(final CharSequence propertyPath) {
        putEntry(propertyPath, ImmutableSortOptionEntry::asc);
        return this;
    }

    private void putEntry(final CharSequence path, final Function<JsonPointer, SortOptionEntry> createEntryFunction) {
        final JsonPointer jsonPointer = JsonFactory.newPointer(path);
        sortOptionEntries.put(jsonPointer, createEntryFunction.apply(jsonPointer));
    }

    @Override
    public SearchQueryBuilder sortDesc(final CharSequence propertyPath) {
        putEntry(propertyPath, ImmutableSortOptionEntry::desc);
        return this;
    }

    @Override
    public SearchQueryBuilder removeSortOptionFor(final CharSequence propertyPath) {
        checkNotNull(propertyPath, "path of the property to remove the sort option for");
        sortOptionEntries.remove(JsonFactory.newPointer(propertyPath));
        return this;
    }

    @Override
    public SearchQueryBuilder limit(final int offset, final int count) {
        checkArgument(offset, o -> (o >= 0), () -> "The offset must not be negative!");
        checkArgument(count, c -> (c > 0), () -> "The count must neither be negative nor zero!");
        checkArgument(count, c -> (c <= ImmutableLimitOption.MAX_COUNT),
                () -> "The count must not be greater than 200!");

        limitOption = ImmutableLimitOption.of(offset, count);
        return this;
    }

    @Override
    public SearchQueryBuilder removeLimitation() {
        limitOption = null;
        return this;
    }

    @Override
    public SearchQuery build() {
        return new ImmutableSearchQuery(this);
    }

    /**
     * A default implementation of {@link SearchQuery}.
     *
     */
    @Immutable
    private static final class ImmutableSearchQuery implements SearchQuery {

        private final SearchFilter filter;
        private final SortOption sortOption;
        private final LimitOption limitOption;

        private ImmutableSearchQuery(final ImmutableSearchQueryBuilder builder) {
            filter = builder.searchFilter;
            final List<SortOptionEntry> sortOptionEntries = new ArrayList<>(builder.sortOptionEntries.values());
            sortOption = !sortOptionEntries.isEmpty() ? ImmutableSortOption.of(sortOptionEntries) : null;
            limitOption = builder.limitOption;
        }

        @Override
        public SearchFilter getFilter() {
            return filter;
        }

        @Override
        public Optional<SortOption> getSortOption() {
            return Optional.ofNullable(sortOption);
        }

        @Override
        public Optional<LimitOption> getLimitOption() {
            return Optional.ofNullable(limitOption);
        }

        @Override
        public Collection<Option> getAllOptions() {
            final List<Option> result = new ArrayList<>(2);
            result.add(sortOption);
            getLimitOption().ifPresent(result::add);
            return result;
        }

        @Override
        public String getFilterAsString() {
            return filter.toString();
        }

        @Override
        public String getOptionsAsString() {
            final StringBuilder stringBuilder = new StringBuilder();

            getLimitOption()
                    .map(Option::toString)
                    .ifPresent(stringBuilder::append);

            getSortOption()
                    .map(Option::toString)
                    .ifPresent(sortOptionString -> {
                        if (0 < stringBuilder.length()) {
                            stringBuilder.append(",");
                        }
                        stringBuilder.append(sortOptionString);
                    });

            return stringBuilder.toString();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + "filter=" + filter + ", sortOption=" + sortOption +
                    ", limitOption="
                    + limitOption + "]";
        }
    }

}
