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

import java.util.Collection;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

/**
 * This interface represents a query for searching properties.
 */
@Immutable
public interface SearchQuery {

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link SearchQuery}.
     *
     * @param filter the search filter which is the crucial part of the search query.
     * @return the new builder.
     * @throws NullPointerException if {@code filter} is {@code null}.
     */
    static SearchQueryBuilder newBuilder(final SearchFilter filter) {
        return SearchModelFactory.newSearchQueryBuilder(filter);
    }

    /**
     * Returns the search filter of this query. This is the main component of the query.
     *
     * @return the search filter of this query.
     */
    SearchFilter getFilter();

    /**
     * Returns the string representation of the search filter.
     *
     * @return the string representation of the search filter.
     * @see SearchFilter#toString()
     */
    String getFilterAsString();

    /**
     * Returns the option which determines how the search result is sorted.
     *
     * @return the option for determining the sort order of the search result.
     */
    Optional<SortOption> getSortOption();

    /**
     * Returns the option which determines pagination of the search result.
     *
     * @return the option for determining the pagination of the search result.
     */
    Optional<LimitOption> getLimitOption();

    /**
     * Returns a collection of all options of this search query. Changes on this collection won't be reflected by the
     * search query.
     *
     * @return the options of this query.
     */
    Collection<Option> getAllOptions();

    /**
     * Returns the string representation of all options combined. For example, if a limit and a sort option is specified
     * in this query, the result might look like {@code "limit(0,25),sort(+thingId,-attributes/manufacturer)"}.
     *
     * @return the string representation of all options combined. If this query does not contain any options, an empty
     * string is returned.
     */
    String getOptionsAsString();

}
