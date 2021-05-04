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
package org.eclipse.ditto.thingsearch.model.assertions;

import java.util.Optional;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.thingsearch.model.LimitOption;
import org.eclipse.ditto.thingsearch.model.SearchFilter;
import org.eclipse.ditto.thingsearch.model.SearchQuery;
import org.eclipse.ditto.thingsearch.model.SortOption;

/**
 * An assert for {@link org.eclipse.ditto.thingsearch.model.SearchQuery}.
 */
public final class SearchQueryAssert extends AbstractAssert<SearchQueryAssert, SearchQuery> {

    /**
     * Constructs a new {@code SearchQueryAssert} object.
     *
     * @param actual the search query to be checked.
     */
    public SearchQueryAssert(final SearchQuery actual) {
        super(actual, SearchQueryAssert.class);
    }

    public SearchQueryAssert hasFilter(final SearchFilter expectedFilter) {
        isNotNull();
        final SearchFilter actualFilter = actual.getFilter();
        Assertions.assertThat(actualFilter) //
                .overridingErrorMessage("Expected the SearchQuery to have the search filter \n<%s> but it had \n<%s>",
                        expectedFilter, actualFilter) //
                .isEqualTo(expectedFilter);
        return this;
    }

    public SearchQueryAssert hasFilterString(final String expectedFilterString) {
        isNotNull();
        final String actualFilterString = actual.getFilterAsString();
        Assertions.assertThat(actualFilterString) //
                .overridingErrorMessage(
                        "Expected the search filter string of the SearchQuery to be \n<%s> but it was " + "\n<%s>",
                        expectedFilterString, actualFilterString) //
                .isEqualTo(expectedFilterString);
        return this;
    }

    public SearchQueryAssert hasNoSortOption() {
        isNotNull();
        final Optional<SortOption> actualSortOption = actual.getSortOption();
        Assertions.assertThat(actualSortOption) //
                .overridingErrorMessage("Expected the SearchQuery not to have a sort option but it had <%s>",
                        actualSortOption) //
                .isEmpty();
        return this;
    }

    public SearchQueryAssert hasSortOption(final SortOption expectedSortOption) {
        isNotNull();
        final Optional<SortOption> actualSortOption = actual.getSortOption();
        Assertions.assertThat(actualSortOption) //
                .overridingErrorMessage(
                        "Expected the SearchQuery to have sort option \n<Optional[%s]> but it had \n<%s>",
                        expectedSortOption, actualSortOption) //
                .contains(expectedSortOption);
        return this;
    }

    public SearchQueryAssert hasNoLimitOption() {
        isNotNull();
        final Optional<LimitOption> actualLimitOption = actual.getLimitOption();
        Assertions.assertThat(actualLimitOption) //
                .overridingErrorMessage("Expected the SearchQuery not to have a limit option but it had <%s>",
                        actualLimitOption) //
                .isEmpty();
        return this;
    }

    public SearchQueryAssert hasLimitOption(final LimitOption expectedLimitOption) {
        isNotNull();
        final Optional<LimitOption> actualLimitOption = actual.getLimitOption();
        Assertions.assertThat(actualLimitOption) //
                .overridingErrorMessage("Expected the SearchQuery to have limit option \n<%s> but it had \n<%s>",
                        expectedLimitOption, actualLimitOption) //
                .contains(expectedLimitOption);
        return this;
    }

    public SearchQueryAssert hasOptionsString(final String expectedOptionsString) {
        isNotNull();
        final String actualOptionsString = actual.getOptionsAsString();
        Assertions.assertThat(actualOptionsString) //
                .overridingErrorMessage("Expected the options string of the SearchQuery to be \n<%s> but it was \n<%s>",
                        expectedOptionsString, actualOptionsString) //
                .isEqualTo(expectedOptionsString);
        return this;
    }

}
