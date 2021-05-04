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

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

/**
 * A logical search filter concatenates other {@link SearchFilter}s with the boolean operators AND or OR. It can
 * negate a single filter, too.
 */
@Immutable
public interface LogicalSearchFilter extends SearchFilter {

    /**
     * Returns an unmodifiable collection of the {@link SearchFilter}s which are concatenated by this logical filter.
     *
     * @return a collection containing the filters which are concatenated by this logical filter.
     */
    Collection<SearchFilter> getFilters();

    /**
     * Returns the string representation of this filter. The string consists of the prefix {@code "and("} which is
     * followed by a comma-separated list of the concatenated filters and finally of the suffix {@code ")"}. An
     * example string might look like {@code "and(eq(thingId,:myThing),gt(attributes/threshold,42.23))"}.
     *
     * @return the string representation of this logical search filter.
     */
    @Override
    String toString();

}
