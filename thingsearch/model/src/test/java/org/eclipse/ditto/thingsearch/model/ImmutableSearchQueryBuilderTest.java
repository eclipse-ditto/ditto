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

import java.util.Arrays;
import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableSearchQueryBuilder}.
 */
public final class ImmutableSearchQueryBuilderTest {

    private static final JsonPointer ATTRIBUTE_PATH =
            JsonFactory.newPointer("attributes/manufacturer");

    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullSearchFilter() {
        ImmutableSearchQueryBuilder.of(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSortAscWithNullJsonPointer() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));
        underTest.sortAsc(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSortAscWithNullString() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));
        underTest.sortAsc(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSortDescWithNullJsonPointer() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));
        underTest.sortDesc(null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToSetSortDescWithNullString() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));
        underTest.sortDesc(null);
    }

    @Test
    public void removeSortOptionWorksAsExpected() {
        final JsonPointer thingId = JsonFactory.newPointer("thingId");

        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(ATTRIBUTE_PATH)
                .sortDesc(thingId)
                .removeSortOptionFor(ATTRIBUTE_PATH.toString())
                .build();

        final ImmutableSortOptionEntry thingIdEntry = ImmutableSortOptionEntry.desc(thingId);
        final ImmutableSortOption expectedSortOption = ImmutableSortOption.of(Collections.singletonList(thingIdEntry));

        assertThat(searchQuery).hasSortOption(expectedSortOption);
    }

    @Test
    public void overwritingSortOptionWorksAsExpected() {
        ImmutableSortOptionEntry attributePathEntry = ImmutableSortOptionEntry.asc(ATTRIBUTE_PATH);
        final ImmutableSortOptionEntry thingIdEntry = ImmutableSortOptionEntry.desc(JsonFactory.newPointer("thingId"));

        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(attributePathEntry.getPropertyPath())
                .sortDesc(thingIdEntry.getPropertyPath())
                .sortDesc(attributePathEntry.getPropertyPath()).build();

        attributePathEntry = ImmutableSortOptionEntry.desc(ATTRIBUTE_PATH);

        final ImmutableSortOption expectedSortOption =
                ImmutableSortOption.of(Arrays.asList(attributePathEntry, thingIdEntry));

        assertThat(searchQuery)
                .hasSortOption(expectedSortOption);
    }

    @Test
    public void buildQueryWithoutSortOption() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));

        assertThat(underTest.build()).hasNoSortOption();
    }

    @Test
    public void buildQueryWithSortOption() {
        final JsonPointer thingId = JsonFactory.newPointer("thingId");

        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(ATTRIBUTE_PATH)
                .sortDesc(thingId)
                .build();

        final ImmutableSortOptionEntry attributePathEntry = ImmutableSortOptionEntry.asc(ATTRIBUTE_PATH);
        final ImmutableSortOptionEntry thingIdEntry = ImmutableSortOptionEntry.desc(thingId);

        final ImmutableSortOption expectedSortOption =
                ImmutableSortOption.of(Arrays.asList(attributePathEntry, thingIdEntry));

        assertThat(searchQuery)
                .hasSortOption(expectedSortOption)
                .hasOptionsString("sort(+/attributes/manufacturer,-/thingId)");
    }

    @Test
    public void buildFullyFledgedSearchQuery() {
        final JsonPointer test = JsonFactory.newPointer("attributes/test");
        final JsonPointer test1 = JsonFactory.newPointer("attributes/test1");
        final boolean boolValue = false;
        final double doubleValue = 42.23D;

        final LogicalSearchFilter searchFilter =
                SearchModelFactory.and(SearchModelFactory.property(test).eq(boolValue),
                        SearchModelFactory.property(test1).gt(doubleValue));

        final SearchQuery searchQuery = SearchModelFactory.newSearchQueryBuilder(searchFilter)
                .sortAsc(test)
                .sortDesc(test1)
                .build();

        assertThat(searchQuery)
                .hasFilter(searchFilter)
                .hasFilterString("and(eq(" + test + ",false)," +
                        "gt(" + test1 + ",42.23))")
                .hasOptionsString("sort(+/attributes/test,-/attributes/test1)");
    }

}
