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

import java.util.Collection;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.thingsearch.model.LogicalSearchFilter;
import org.eclipse.ditto.thingsearch.model.SearchFilter;

/**
 * An assert for {@link org.eclipse.ditto.thingsearch.model.SearchFilter}.
 */
public final class LogicalSearchFilterAssert
        extends SearchFilterAssert<LogicalSearchFilterAssert, LogicalSearchFilter> {

    /**
     * Constructs a new {@code LogicalSearchFilterAssert} object.
     *
     * @param actual the search query to be checked.
     */
    public LogicalSearchFilterAssert(final LogicalSearchFilter actual) {
        super(actual, LogicalSearchFilterAssert.class);
    }

    public LogicalSearchFilterAssert containsOnlyFilter(final SearchFilter... expectedFilters) {
        isNotNull();
        final Collection<SearchFilter> actualFilters = actual.getFilters();
        Assertions.assertThat(actualFilters) //
                .overridingErrorMessage(
                        "Expected LogicalSearchFilter to contain the filter(s) \n<%s> but it contained \n<%s>",
                        expectedFilters, actualFilters) //
                .containsOnly(expectedFilters);
        return this;
    }

    public LogicalSearchFilterAssert containsAmountOfFilters(final int expectedAmount) {
        isNotNull();
        final Collection<SearchFilter> filters = actual.getFilters();
        final int actualAmountOfFilters = filters.size();
        Assertions.assertThat(actualAmountOfFilters) //
                .overridingErrorMessage(
                        "Expected amount of filters of LogicalSearchFilter to be \n<%s> but it was \n<%s>",
                        expectedAmount, actualAmountOfFilters) //
                .isEqualTo(expectedAmount);
        return this;
    }

}
