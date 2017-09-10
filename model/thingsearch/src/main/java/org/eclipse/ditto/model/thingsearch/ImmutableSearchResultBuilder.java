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
package org.eclipse.ditto.model.thingsearch;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

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
    private long offset;

    private ImmutableSearchResultBuilder(final JsonArrayBuilder theJsonArrayBuilder, final long theOffset) {
        jsonArrayBuilder = theJsonArrayBuilder;
        offset = theOffset;
    }

    /**
     * Returns a new instance of {@code ImmutableSearchResultBuilder}.
     *
     * @return a new builder.
     */
    public static ImmutableSearchResultBuilder newInstance() {
        return new ImmutableSearchResultBuilder(JsonFactory.newArrayBuilder(), SearchResult.NO_NEXT_PAGE);
    }

    /**
     * Returns a new instance of {@code ImmutableSearchResultBuilder} which is initialised with the properties of the
     * given Search result.
     *
     * @param searchResult the search result which provides the initial properties of the returned builder.
     * @return the new builder.
     * @throws NullPointerException if {@code searchResult} is null.
     */
    public static ImmutableSearchResultBuilder of(final SearchResult searchResult) {
        checkNotNull(searchResult, "search result");

        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder(searchResult.getItems());

        return new ImmutableSearchResultBuilder(jsonArrayBuilder, searchResult.getNextPageOffset());
    }

    @Override
    public SearchResultBuilder nextPageOffset(final long nextPageOffset) {
        offset = nextPageOffset;
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
        return ImmutableSearchResult.of(searchResultsJsonArray, offset);
    }

}
