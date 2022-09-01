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
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingsModelFactory;
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
    public void newSearchResultEmpty() {
        final SearchResult searchResult = SearchModelFactory.emptySearchResult();

        assertThat(searchResult).isEmpty();
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
        SearchModelFactory.property(null);
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

    @Test
    public void newSortOptionFromSingleEntry() {
        final SortOptionEntry.SortOrder sortOrder = SortOptionEntry.SortOrder.DESC;
        final String propertyPath = THING_ID_KEY;

        final SortOption actual = SearchModelFactory.newSortOption(propertyPath, sortOrder);

        final SortOptionEntry expectedEntry = SearchModelFactory.newSortOptionEntry(propertyPath, sortOrder);
        final SortOption expected =
                SearchModelFactory.newSortOption(Collections.singletonList(expectedEntry));
        assertThat(actual).isEqualTo(expected);
    }

}
