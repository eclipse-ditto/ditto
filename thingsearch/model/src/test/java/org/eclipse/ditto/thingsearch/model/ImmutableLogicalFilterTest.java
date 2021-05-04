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
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableLogicalFilter}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ImmutableLogicalFilterTest {

    @Mock
    private SearchFilter searchFilterMock;

    private static SearchFilter createFilterMock(final String filterString) {
        final SearchFilter result = mock(SearchFilter.class);
        when(result.toString()).thenReturn(filterString);
        return result;
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableLogicalFilter.class,
                areImmutable(),
                provided(SearchFilter.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableLogicalFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateNotFilterWithNullFilter() {
        ImmutableLogicalFilter.not(null);
    }

    @Test
    public void createNotFilter() {
        final ImmutableLogicalFilter searchFilter = ImmutableLogicalFilter.not(searchFilterMock);

        assertThat(searchFilter)
                .hasType(SearchFilter.Type.NOT)
                .containsOnlyFilter(searchFilterMock);
    }

    @Test
    public void toStringOfNotFilterReturnsExpected() {
        final SearchFilter.Type filterType = SearchFilter.Type.NOT;
        final SearchFilter filter = createFilterMock("waldo");
        final String expected = filterType.getName() + "(" + filter.toString() + ")";

        final ImmutableLogicalFilter underTest = ImmutableLogicalFilter.not(filter);

        assertThat(underTest).hasStringRepresentation(expected);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullFirstFilter() {
        ImmutableLogicalFilter.and(null, searchFilterMock);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullSecondFilter() {
        ImmutableLogicalFilter.and(searchFilterMock, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateAndFilterWithNullAdditionalFilters() {
        ImmutableLogicalFilter.and(searchFilterMock, searchFilterMock, null);
    }

    @Test
    public void createAndFilter() {
        final ImmutableLogicalFilter underTest =
                ImmutableLogicalFilter.and(searchFilterMock, searchFilterMock, searchFilterMock, searchFilterMock);

        assertThat(underTest)
                .hasType(SearchFilter.Type.AND)
                .containsAmountOfFilters(4)
                .containsOnlyFilter(searchFilterMock);
    }

    @Test
    public void toStringOfAndFilterReturnsExpected() {
        final SearchFilter.Type filterType = SearchFilter.Type.AND;
        final SearchFilter filter1 = createFilterMock("filter1");
        final SearchFilter filter2 = createFilterMock("filter2");
        final String expected = filterType.getName() + "(" + filter1.toString() + "," + filter2.toString() + ")";

        final ImmutableLogicalFilter underTest = ImmutableLogicalFilter.and(filter1, filter2);

        assertThat(underTest).hasStringRepresentation(expected);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullFirstFilter() {
        ImmutableLogicalFilter.or(null, searchFilterMock);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullSecondFilter() {
        ImmutableLogicalFilter.or(searchFilterMock, null);
    }

    @Test(expected = NullPointerException.class)
    public void tryToCreateOrFilterWithNullAdditionalFilters() {
        ImmutableLogicalFilter.or(searchFilterMock, searchFilterMock, null);
    }

    @Test
    public void createOrFilter() {
        final ImmutableLogicalFilter underTest =
                ImmutableLogicalFilter.or(searchFilterMock, searchFilterMock, searchFilterMock, searchFilterMock);

        assertThat(underTest)
                .hasType(SearchFilter.Type.OR)
                .containsAmountOfFilters(4)
                .containsOnlyFilter(searchFilterMock);
    }

    @Test
    public void toStringOfOrFilterReturnsExpected() {
        final SearchFilter.Type filterType = SearchFilter.Type.OR;
        final SearchFilter filter1 = createFilterMock("filter1");
        final SearchFilter filter2 = createFilterMock("filter2");
        final String expected = filterType.getName() + "(" + filter1.toString() + "," + filter2.toString() + ")";

        final ImmutableLogicalFilter underTest = ImmutableLogicalFilter.or(filter1, filter2);

        assertThat(underTest).hasStringRepresentation(expected);
    }

}
