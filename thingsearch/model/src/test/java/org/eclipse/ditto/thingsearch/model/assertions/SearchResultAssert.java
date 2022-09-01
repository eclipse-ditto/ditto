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

import org.assertj.core.api.AbstractIterableAssert;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.eclipse.ditto.json.assertions.JsonValueAssert;
import org.eclipse.ditto.thingsearch.model.SearchResult;

/**
 * An assert for {@link org.eclipse.ditto.thingsearch.model.SearchResult}.
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

    @Override
    protected JsonValueAssert toAssert(final JsonValue value, final String description) {
        return DittoJsonAssertions.assertThat(value).as(description);
    }

    @Override
    protected SearchResultAssert newAbstractIterableAssert(final Iterable<? extends JsonValue> iterable) {
        return new SearchResultAssert((SearchResult) iterable);
    }

}
