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
