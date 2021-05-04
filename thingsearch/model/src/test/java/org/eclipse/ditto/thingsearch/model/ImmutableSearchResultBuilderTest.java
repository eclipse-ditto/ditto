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

import static org.eclipse.ditto.thingsearch.model.assertions.DittoSearchAssertions.assertThat;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableSearchResultBuilder}.
 */
public final class ImmutableSearchResultBuilderTest {

    private SearchResultBuilder underTest = null;

    @Before
    public void setUp() {
        underTest = ImmutableSearchResultBuilder.newInstance();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNewInstanceWithNullSearchResult() {
        ImmutableSearchResultBuilder.of(null);
    }

    @Test
    public void copySearchResultWithBuilder() {
        final JsonArray items = JsonFactory.newArrayBuilder().add("foo", "bar", "baz").build();
        final long offset = 25;
        final SearchResult searchResult = SearchModelFactory.newSearchResult(items, offset);

        final SearchResultBuilder underTest = ImmutableSearchResultBuilder.of(searchResult);

        assertThat(underTest.build()).isEqualTo(searchResult);
    }

    @Test
    public void copyAndModifyExistingSearchResultWithBuilder() {
        final JsonArray items = JsonFactory.newArrayBuilder().add("foo", "bar", "baz").build();
        final long offset = 25;
        final SearchResult searchResult = SearchModelFactory.newSearchResult(items, offset);

        final SearchResult newSearchResult = ImmutableSearchResultBuilder.of(searchResult)
                .nextPageOffset(SearchResult.NO_NEXT_PAGE)
                .build();

        assertThat(newSearchResult)
                .hasNoNextPage()
                .containsExactlyElementsOf(searchResult.getItems());
    }

    @Test
    public void createEmptySearchResult() {
        final SearchResult searchResult = underTest.build();

        assertThat(searchResult)
                .hasNoNextPage()
                .isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddNullItem() {
        underTest.add(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToRemoveNullItem() {
        underTest.remove(null);
    }

    @Test
    public void addThingItem() {
        final SearchResult searchResult = underTest.add(TestConstants.SearchThing.THING.toJson())
                .build();

        assertThat(searchResult)
                .hasNoNextPage()
                .contains(TestConstants.SearchThing.THING.toJson());
    }

    @Test
    public void addAndRemoveThingItem() {
        final SearchResult searchResult = underTest.add(TestConstants.SearchThing.THING.toJson())
                .remove(TestConstants.SearchThing.THING.toJson())
                .build();

        assertThat(searchResult)
                .hasNoNextPage()
                .isEmpty();
    }

    @Test
    public void setNextPageOffset() {
        final long expectedNextPageOffset = 20L;
        final SearchResult searchResult = underTest.nextPageOffset(expectedNextPageOffset).build();

        assertThat(searchResult)
                .hasNextPageOffset(expectedNextPageOffset)
                .isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void tryToAddAllNull() {
        ImmutableSearchResultBuilder.newInstance()
                .addAll(null)
                .build();
    }

    @Test
    public void addItemsWorksAsExpected() {
        final JsonArray items = JsonFactory.newArrayBuilder()
                .add(1, 2, 3)
                .build();
        final SearchResult searchResult = ImmutableSearchResultBuilder.newInstance()
                .addAll(items)
                .build();

        assertThat(searchResult)
                .containsExactlyElementsOf(items)
                .hasNoNextPage();
    }

}
