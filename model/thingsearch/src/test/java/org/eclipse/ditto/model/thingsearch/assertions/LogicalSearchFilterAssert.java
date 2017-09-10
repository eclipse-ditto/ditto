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

import java.util.Collection;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.thingsearch.LogicalSearchFilter;
import org.eclipse.ditto.model.thingsearch.SearchFilter;

/**
 * An assert for {@link SearchFilter}.
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
