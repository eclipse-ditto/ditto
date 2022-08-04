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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;

/**
 * A mutable builder for an {@link ImmutableSearchResult} with a fluent API.
 */
@NotThreadSafe
final class ImmutableSearchResultBuilder implements SearchResultBuilder {

    private final JsonArrayBuilder jsonArrayBuilder;
    @Nullable private Long offset;
    @Nullable private String cursor;

    private ImmutableSearchResultBuilder(final JsonArrayBuilder theJsonArrayBuilder) {
        jsonArrayBuilder = theJsonArrayBuilder;
    }

    /**
     * Returns a new instance of {@code ImmutableSearchResultBuilder}.
     *
     * @return a new builder.
     */
    public static SearchResultBuilder newInstance() {
        return new ImmutableSearchResultBuilder(JsonFactory.newArrayBuilder())
                .nextPageOffset(SearchResult.NO_NEXT_PAGE);
    }

    /**
     * Returns a new instance of {@code ImmutableSearchResultBuilder} which is initialised with the properties of the
     * given Search result.
     *
     * @param searchResult the search result which provides the initial properties of the returned builder.
     * @return the new builder.
     * @throws NullPointerException if {@code searchResult} is null.
     */
    public static SearchResultBuilder of(final SearchResult searchResult) {
        checkNotNull(searchResult, "search result");

        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder(searchResult.getItems());
        final Long nextPageOffset = searchResult.getNextPageOffset().orElse(null);
        final String cursor = searchResult.getCursor().orElse(null);

        return new ImmutableSearchResultBuilder(jsonArrayBuilder).nextPageOffset(nextPageOffset)
                .cursor(cursor);
    }

    @Override
    public SearchResultBuilder nextPageOffset(@Nullable final Long nextPageOffset) {
        offset = nextPageOffset;
        return this;
    }

    @Override
    public SearchResultBuilder cursor(@Nullable final String cursor) {
        this.cursor = cursor;
        return this;
    }

    @Override
    public SearchResultBuilder add(final JsonValue value, final JsonValue... furtherValues) {
        jsonArrayBuilder.add(value, furtherValues);
        return this;
    }

    @Override
    public SearchResultBuilder addAll(final Iterable<? extends JsonValue> values) {
        jsonArrayBuilder.addAll(values);
        return this;
    }

    @Override
    public SearchResultBuilder remove(final JsonValue value) {
        jsonArrayBuilder.remove(value);
        return this;
    }

    @Override
    public SearchResult build() {
        final JsonArray searchResultsJsonArray = jsonArrayBuilder.build();
        return ImmutableSearchResult.of(searchResultsJsonArray, offset, cursor);
    }

}
