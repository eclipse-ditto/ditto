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
package org.eclipse.ditto.model.thingsearch.assertions;

import org.eclipse.ditto.model.base.assertions.DittoBaseAssertions;
import org.eclipse.ditto.model.thingsearch.LogicalSearchFilter;
import org.eclipse.ditto.model.thingsearch.PropertySearchFilter;
import org.eclipse.ditto.model.thingsearch.SearchQuery;
import org.eclipse.ditto.model.thingsearch.SearchResult;

/**
 * Project specific {@link org.assertj.core.api.Assertions} to extends the set of assertions which are provided by FEST.
 */
public class DittoSearchAssertions extends DittoBaseAssertions {

    /**
     * Returns an assert for the given {@link SearchResult}.
     *
     * @param searchResult the SearchResult to be checked.
     * @return the assert for {@code searchResult}.
     */
    public static SearchResultAssert assertThat(final SearchResult searchResult) {
        return new SearchResultAssert(searchResult);
    }

    /**
     * Returns an assert for the given {@link SearchQuery}.
     *
     * @param searchQuery the SearchQuery to be checked.
     * @return the assert for {@code searchQuery}.
     */
    public static SearchQueryAssert assertThat(final SearchQuery searchQuery) {
        return new SearchQueryAssert(searchQuery);
    }

    /**
     * Returns an assert for the given {@link LogicalSearchFilter}.
     *
     * @param searchFilter the LogicalSearchFilter to be checked.
     * @return the assert for {@code searchFilter}.
     */
    public static LogicalSearchFilterAssert assertThat(final LogicalSearchFilter searchFilter) {
        return new LogicalSearchFilterAssert(searchFilter);
    }

    /**
     * Returns an assert for the given {@link PropertySearchFilter}.
     *
     * @param searchFilter the PropertySearchFilter to be checked.
     * @return the assert for {@code searchFilter}.
     */
    public static PropertySearchFilterAssert assertThat(final PropertySearchFilter searchFilter) {
        return new PropertySearchFilterAssert(searchFilter);
    }

}
