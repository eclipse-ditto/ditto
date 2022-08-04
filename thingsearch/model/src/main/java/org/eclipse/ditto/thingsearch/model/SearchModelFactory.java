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

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.base.model.exceptions.DittoJsonException.wrapJsonRuntimeException;

import java.util.Collections;
import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Factory for creating search results.
 * <p>
 * This factory provides static methods for creating {@link LogicalSearchFilter}s or {@link SearchProperty search
 * properties}. The former are useful to concatenate {@link SearchFilter}s with boolean operators while the latter are
 * mandatory for a building a {@link SearchQuery} .
 */
@Immutable
public final class SearchModelFactory {

    /*
     * Inhibit instantiation of this utility class.
     */
    private SearchModelFactory() {
        throw new AssertionError();
    }

    /**
     * Creates a new {@link SearchResult}.
     *
     * @param items the items.
     * @param nextPageOffset the offset of the next page or {@link org.eclipse.ditto.thingsearch.model.SearchResult#NO_NEXT_PAGE}.
     * @return the new immutable search results object.
     * @throws NullPointerException if {@code items} is {@code null}.
     */
    public static SearchResult newSearchResult(final JsonArray items, final long nextPageOffset) {
        return ImmutableSearchResult.of(items, nextPageOffset, null);
    }

    /**
     * Returns a new immutable empty {@link SearchResult}.
     *
     * @return the new immutable empty {@code SearchResult}.
     */
    public static SearchResult emptySearchResult() {
        return ImmutableSearchResult.empty();
    }

    /**
     * Returns a new immutable {@link SearchResult} which is initialised with the values of the given JSON object.
     *
     * @param jsonObject provides the initial values of the result.
     * @return the new immutable initialised {@code SearchResult}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static SearchResult newSearchResult(final JsonObject jsonObject) {
        return wrapJsonRuntimeException(() -> ImmutableSearchResult.fromJson(jsonObject));
    }

    /**
     * Returns a new immutable {@link SearchResult} which is initialised with the values of the given JSON string. This
     * string is required to be a valid {@link JsonObject}.
     *
     * @param jsonString provides the initial values of the result;
     * @return the new immutable initialised {@code SearchResult}.
     * @throws org.eclipse.ditto.base.model.exceptions.DittoJsonException if {@code jsonString} cannot be parsed to {@code
     * SearchResult}.
     */
    public static SearchResult newSearchResult(final String jsonString) {
        final JsonObject jsonObject = wrapJsonRuntimeException(() -> JsonFactory.newObject(jsonString));
        return newSearchResult(jsonObject);
    }

    /**
     * Returns a new builder with a fluent API for a {@link SearchResult}.
     *
     * @return the new builder.
     */
    public static SearchResultBuilder newSearchResultBuilder() {
        return ImmutableSearchResultBuilder.newInstance();
    }

    /**
     * Returns a new builder with a fluent API for a {@link SearchResult} which is initialised with the properties of
     * the given SearchResult.
     *
     * @param searchResult the search result which provides the initial properties of the returned builder.
     * @return the new builder.
     * @throws NullPointerException if {@code searchResult} is {@code null}.
     */
    public static SearchResultBuilder newSearchResultBuilder(final SearchResult searchResult) {
        return ImmutableSearchResultBuilder.of(searchResult);
    }

    /**
     * Returns a new {@link LogicalSearchFilter} which concatenates the given filters with the boolean operator
     * {@code AND}.
     *
     * @param filter1 the first filter to be concatenated with at least {@code filter2} and optionally additionally with
     * {@code furtherFilters}.
     * @param filter2 the second filter to be concatenated with {@code filter1} and {@code furtherFilters}.
     * @param furtherFilters optional additional filters to be concatenated.
     * @return the new logical {@code AND} search filter.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static LogicalSearchFilter and(final SearchFilter filter1, final SearchFilter filter2,
            final SearchFilter... furtherFilters) {
        return ImmutableLogicalFilter.and(filter1, filter2, furtherFilters);
    }

    /**
     * Returns a new {@link LogicalSearchFilter} which concatenates the given filters with the boolean operator
     * {@code OR}.
     *
     * @param filter1 the first filter to be concatenated with at least {@code filter2} and optionally additionally with
     * {@code furtherFilters}.
     * @param filter2 the second filter to be concatenated with {@code filter1} and {@code furtherFilters}.
     * @param furtherFilters optional additional filters to be concatenated.
     * @return the new logical {@code OR} search filter.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static LogicalSearchFilter or(final SearchFilter filter1, final SearchFilter filter2,
            final SearchFilter... furtherFilters) {
        return ImmutableLogicalFilter.or(filter1, filter2, furtherFilters);
    }

    /**
     * Returns a new {@link LogicalSearchFilter} which negates the given filter with the boolean operator {@code NOT}.
     *
     * @param filter the filter to be negated.
     * @return the new logical {@code NOT} filter.
     * @throws NullPointerException if {@code filter} is {@code null}.
     */
    public static LogicalSearchFilter not(final SearchFilter filter) {
        return ImmutableLogicalFilter.not(filter);
    }

    /**
     * Creates a {@link SearchProperty} for the given path. For example to search for a particular Thing attribute this
     * method might be used as follows:
     * <pre>
     * final SearchProperty searchProperty = SearchModelFactory.property("attributes/manufacturer");
     * final PropertySearchFilter searchFilter = searchProperty.eq("Bosch");
     *
     * final SearchQuery query = SearchModelFactory.newSearchQueryBuilder(searchFilter) //
     *    .limit(0, 25) //
     *    .build();
     * </pre>
     *
     * @param propertyPath the name or path of the property to be searched for.
     * @return the new SearchProperty.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static SearchProperty property(final JsonPointer propertyPath) {
        return ImmutableSearchProperty.of(propertyPath);
    }

    /**
     * Creates a {@link SearchProperty} for the given path. For example to search for a particular Thing attribute this
     * method might be used as follows:
     * <pre>
     * final SearchProperty searchProperty = SearchModelFactory.property("attributes/manufacturer");
     * final PropertySearchFilter searchFilter = searchProperty.eq("Bosch");
     *
     * final SearchQuery query = SearchModelFactory.newSearchQueryBuilder(searchFilter) //
     *    .limit(0, 25) //
     *    .build();
     * </pre>
     *
     * @param propertyPath the name or path of the property to be searched for.
     * @return the new SearchProperty.
     * @throws NullPointerException if {@code propertyPath} is {@code null}.
     */
    public static SearchProperty property(final CharSequence propertyPath) {
        return property(JsonFactory.newPointer(propertyPath));
    }

    /**
     * Returns a new immutable {@link SearchQuery} which is based on the given search filter.
     *
     * @param filter the search filter which is the crucial part of the search query.
     * @return the new SearchQuery.
     * @throws NullPointerException if {@code filter} is {@code null}.
     */
    public static SearchQuery newSearchQuery(final SearchFilter filter) {
        return newSearchQueryBuilder(filter).build();
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link SearchQuery}.
     *
     * @param filter the search filter which is the crucial part of the search query.
     * @return the new builder.
     * @throws NullPointerException if {@code filter} is {@code null}.
     */
    public static SearchQueryBuilder newSearchQueryBuilder(final SearchFilter filter) {
        return ImmutableSearchQueryBuilder.of(filter);
    }

    /**
     * Creates a new {@link SortOption} for the provided {@code sortOptionEntries} containing {@code propertyPath}
     * and {@code order}.
     *
     * @param sortOptionEntries the {@link SortOptionEntry}s containing {@code propertyPath} and {@code order}
     * @return the created SortOption.
     */
    public static SortOption newSortOption(final List<SortOptionEntry> sortOptionEntries) {
        return ImmutableSortOption.of(sortOptionEntries);
    }

    /**
     * Creates a new {@link SortOption} with a single entry for the provided {@code order} and {@code propertyPath}.
     *
     * @param propertyPath the {@code propertyPath} of the SortOptionEntry to create
     * @param sortOrder the {@code order} of the SortOptionEntry to create
     * @return the created SortOption.
     */
    public static SortOption newSortOption(final CharSequence propertyPath, final SortOptionEntry.SortOrder sortOrder) {
        requireNonNull(sortOrder);
        requireNonNull(propertyPath);

        final SortOptionEntry entry = ImmutableSortOptionEntry.of(propertyPath, sortOrder);
        return ImmutableSortOption.of(Collections.singletonList(entry));
    }

    /**
     * Creates a new {@link SortOptionEntry} for the provided {@code order} and {@code propertyPath}.
     *
     * @param propertyPath the {@code propertyPath} of the SortOptionEntry to create
     * @param sortOrder the {@code order} of the SortOptionEntry to create
     * @return the created SortOption.
     */
    public static SortOptionEntry newSortOptionEntry(final CharSequence propertyPath,
            final SortOptionEntry.SortOrder sortOrder) {

        return ImmutableSortOptionEntry.of(propertyPath, sortOrder);
    }

    /**
     * Creates a new {@link LimitOption} for the provided {@code offset} and {@code count}.
     *
     * @param offset the offset to use for the LimitOption
     * @param count the count to include in the LimitOption
     * @return the created LimitOption
     */
    public static LimitOption newLimitOption(final int offset, final int count) {
        return ImmutableLimitOption.of(offset, count);
    }

    /**
     * Creates a new {@link SizeOption}.
     *
     * @param size the maximum number of results
     * @return the SizeOption
     */
    public static SizeOption newSizeOption(final int size) {
        return ImmutableSizeOption.of(size);
    }

    /**
     * Creates a new {@link CursorOption}.
     *
     * @param cursor cursor of the new  page
     * @return the CursorOption
     */
    public static CursorOption newCursorOption(final String cursor) {
        return ImmutableCursorOption.of(checkNotNull(cursor, "cursor"));
    }

}
