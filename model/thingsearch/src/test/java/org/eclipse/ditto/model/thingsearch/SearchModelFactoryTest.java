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

import static org.eclipse.ditto.model.thingsearch.assertions.DittoSearchAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link SearchModelFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SearchModelFactoryTest {

    private static final String THING_ID_KEY = Thing.JsonFields.ID.getPointer().toString();

    @Mock
    private SearchFilter filterMock;


    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingsModelFactory.class, areImmutable());
    }


    @Test
    public void newSearchResult() {
        final SearchResult searchResult =
                SearchModelFactory.newSearchResult(JsonFactory.newArray(), SearchResult.NO_NEXT_PAGE);

        assertThat(searchResult) //
                .hasNoNextPage() //
                .isEmpty();
    }


    @Test
    public void newSearchResultFromJson() {
        final JsonArray itemsArray = JsonFactory.newArrayBuilder() //
                .add(1, 2, 3) //
                .build();
        final long nextPageOffset = 10L;
        final String jsonStr = "{\"items\":" + itemsArray.toString() + ",\"nextPageOffset\":" + nextPageOffset + "}";
        final JsonObject jsonObject = JsonFactory.newObject(jsonStr);

        final SearchResult searchResult = SearchModelFactory.newSearchResult(jsonObject);

        assertThat(searchResult) //
                .containsExactlyElementsOf(itemsArray) //
                .hasNextPageOffset(nextPageOffset);
    }


    @Test
    public void newSearchResultFromJsonString() {
        final JsonArray itemsArray = JsonFactory.newArrayBuilder() //
                .add(1, 2, 3) //
                .build();
        final long nextPageOffset = 10L;
        final String jsonStr = "{\"items\":" + itemsArray.toString() + ",\"nextPageOffset\":" + nextPageOffset + "}";

        final SearchResult searchResult = SearchModelFactory.newSearchResult(jsonStr);

        assertThat(searchResult) //
                .containsExactlyElementsOf(itemsArray) //
                .hasNextPageOffset(nextPageOffset);
    }


    @Test
    public void newSearchResultEmpty() {
        final SearchResult searchResult = SearchModelFactory.emptySearchResult();

        assertThat(searchResult) //
                .hasNoNextPage() //
                .isEmpty();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullFilter1() {
        SearchModelFactory.and(null, filterMock);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullFilter2() {
        SearchModelFactory.and(filterMock, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullOptionalFilters() {
        SearchModelFactory.and(filterMock, filterMock, null);
    }


    @Test
    public void createAndFilterWithMultipleFilters() {
        final LogicalSearchFilter searchFilter = SearchModelFactory.and(filterMock, filterMock, filterMock, filterMock);
        final Collection<SearchFilter> filters = searchFilter.getFilters();

        assertThat(filters).hasSize(4);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullFilter1() {
        SearchModelFactory.or(null, filterMock);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullFilter2() {
        SearchModelFactory.or(filterMock, null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullOptionalFilters() {
        SearchModelFactory.or(filterMock, filterMock, null);
    }


    @Test
    public void createOrFilterWithMultipleFilters() {
        final LogicalSearchFilter searchFilter = SearchModelFactory.or(filterMock, filterMock, filterMock, filterMock);
        final Collection<SearchFilter> filters = searchFilter.getFilters();

        assertThat(filters).hasSize(4);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateNotFilterWithNullFilter() {
        SearchModelFactory.not(null);
    }


    @Test
    public void createNotFilter() {
        final LogicalSearchFilter searchFilter = SearchModelFactory.not(filterMock);
        final Collection<SearchFilter> filters = searchFilter.getFilters();

        assertThat(filters).containsOnly(filterMock);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateSearchPropertyWithNullJsonPointer() {
        SearchModelFactory.property((JsonPointer) null);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateSearchPropertyWithNullString() {
        SearchModelFactory.property((String) null);
    }


    @Test
    public void createSearchProperty() {
        final SearchProperty searchProperty = SearchModelFactory.property(THING_ID_KEY);

        assertThat(searchProperty.getPath().toString()).isEqualTo(THING_ID_KEY);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateSearchQueryWithNullFilter() {
        SearchModelFactory.newSearchQuery(null);
    }


    @Test
    public void newSearchQueryReturnsExpected() {
        final SearchQuery searchQuery = SearchModelFactory.newSearchQuery(mock(SearchFilter.class));

        assertThat(searchQuery).isNotNull();
    }

}
