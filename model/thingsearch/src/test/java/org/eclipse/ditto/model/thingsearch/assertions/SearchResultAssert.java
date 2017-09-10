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

import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.json.assertions.JsonValueAssert;
import org.eclipse.ditto.model.thingsearch.SearchResult;

/**
 * An assert for {@link SearchResult}.
 */
public final class SearchResultAssert extends AbstractIterableAssert<SearchResultAssert, SearchResult, JsonValue,
        JsonValueAssert> {

    /**
     * Constructs a new {@code SearchResultAssert} object.
     *
     * @param actual the SearchResult to be checked.
     */
    public SearchResultAssert(final SearchResult actual) {
        super(actual, SearchResultAssert.class);
    }

    public SearchResultAssert hasNextPageOffset(final long expectedNextPageOffset) {
        isNotNull();
        final long actualNextPageOffset = actual.getNextPageOffset();
        Assertions.assertThat(actualNextPageOffset) //
                .overridingErrorMessage("Expected SearchResult to have next page offset \n<%s> but it had \n<%s>",
                        expectedNextPageOffset, actualNextPageOffset) //
                .isEqualTo(expectedNextPageOffset);
        return this;
    }

    public SearchResultAssert hasNoNextPage() {
        isNotNull();
        final long actualNextPageOffset = actual.getNextPageOffset();
        Assertions.assertThat(actualNextPageOffset) //
                .overridingErrorMessage("Expected SearchResult not to have a next page offset but it had <%s>",
                        actualNextPageOffset) //
                .isEqualTo(SearchResult.NO_NEXT_PAGE);
        return this;
    }

    @Override
    protected JsonValueAssert toAssert(final JsonValue value, final String description) {
        return DittoJsonAssertions.assertThat(value).as(description);
    }

}
