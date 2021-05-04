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
import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableSearchQueryBuilder}.
 */
public final class ImmutableSearchQueryBuilderTest {

    private static final JsonPointer ATTRIBUTE_PATH = JsonFactory.newPointer("attributes/manufacturer");
    private static final int KNOWN_OFFSET = 3;
    private static final int KNOWN_COUNT = 12;

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
                .hasNoLimitOption()
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

    @Test(expected = IllegalArgumentException.class)
    public void tryToSetLimitOptionWithNegativeOffset() {
        ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(ATTRIBUTE_PATH)
                .limit(-1, KNOWN_COUNT)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToSetLimitOptionWithNegativeCount() {
        ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(ATTRIBUTE_PATH)
                .limit(KNOWN_OFFSET, -1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void tryToSetLimitOptionWithTooBigCount() {
        ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .sortAsc(ATTRIBUTE_PATH)
                .limit(KNOWN_OFFSET, ImmutableLimitOption.MAX_COUNT + 1)
                .build();
    }

    @Test
    public void removeLimitOptionWorksAsExpected() {
        final ImmutableSortOptionEntry attributePathEntry = ImmutableSortOptionEntry.asc(ATTRIBUTE_PATH);

        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .limit(KNOWN_OFFSET, KNOWN_COUNT)
                .sortAsc(attributePathEntry.getPropertyPath())
                .removeLimitation()
                .build();

        assertThat(searchQuery)
                .hasNoLimitOption()
                .hasSortOption(ImmutableSortOption.of(Collections.singletonList(attributePathEntry)));
    }

    @Test
    public void buildQueryWithoutLimitOption() {
        final ImmutableSearchQueryBuilder underTest = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class));

        assertThat(underTest.build()).hasNoLimitOption();
    }

    @Test
    public void buildQueryWithLimitOption() {
        final byte offset = 3;
        final byte count = 5;

        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .limit(offset, count)
                .build();

        final ImmutableLimitOption expectedLimitOption = ImmutableLimitOption.of(offset, count);

        assertThat(searchQuery)
                .hasLimitOption(expectedLimitOption)
                .hasOptionsString("limit(3,5)");
    }

    @Test
    public void getAllOptionsReturnsExpected() {
        final byte offset = 3;
        final byte count = 5;
        final SearchQuery searchQuery = ImmutableSearchQueryBuilder.of(mock(SearchFilter.class))
                .limit(offset, count)
                .sortDesc(ATTRIBUTE_PATH)
                .build();

        final ImmutableLimitOption expectedLimitOption = ImmutableLimitOption.of(offset, count);
        final ImmutableSortOptionEntry attributePathEntry = ImmutableSortOptionEntry.desc(ATTRIBUTE_PATH);
        final ImmutableSortOption expectedSortOption =
                ImmutableSortOption.of(Collections.singletonList(attributePathEntry));

        final Collection<Option> allOptions = searchQuery.getAllOptions();

        assertThat(allOptions).containsOnly(expectedLimitOption, expectedSortOption);
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
                .limit(0, 25)
                .sortAsc(test)
                .sortDesc(test1)
                .build();

        assertThat(searchQuery)
                .hasFilter(searchFilter)
                .hasFilterString("and(eq(" + test + ",false)," +
                        "gt(" + test1 + ",42.23))")
                .hasOptionsString("limit(0,25),sort(+/attributes/test,-/attributes/test1)");
    }

}
