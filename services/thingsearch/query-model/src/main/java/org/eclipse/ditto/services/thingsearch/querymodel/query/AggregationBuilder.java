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
package org.eclipse.ditto.services.thingsearch.querymodel.query;

import java.util.Collection;
import java.util.List;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;

/**
 * Defines a Builder for a {@link PolicyRestrictedSearchAggregation}.
 */
public interface AggregationBuilder {

    /**
     * Specify SortOptions. Overwrites existing SortOptions.
     *
     * @param sortOptions the SortOptions.
     * @return this builder.
     * @throws NullPointerException if {@code sortOptions} is {@code null}.
     */
    AggregationBuilder sortOptions(List<SortOption> sortOptions);

    /**
     * Limits the number of elements returned by the query.
     *
     * @param n the (maximum) number of elements to be returned.
     * @return this builder.
     */
    AggregationBuilder limit(long n);

    /**
     * Discards the given number of elements at the beginning of the query result.
     *
     * @param n the number of elements to be skipped.
     * @return this builder.
     */
    AggregationBuilder skip(long n);

    /**
     * Sets the filter criteria used for filtering things.
     *
     * @param filterCriteria the criteria.
     * @return the builder.
     * @throws NullPointerException if {@code filterCriteria} is {@code null}.
     */
    AggregationBuilder filterCriteria(Criteria filterCriteria);

    /**
     * Sets the authorization subjects used for filtering.
     *
     * @param authorizationSubjects the authorization subjects.
     * @return the builder.
     * @throws NullPointerException if {@code authorizationSubjects} is {@code null}.
     */
    AggregationBuilder authorizationSubjects(Collection<String> authorizationSubjects);

    /**
     * Sets whether this aggregation should also consider and return as deleted marked things.
     *
     * @param withDeletedThings if {@code true} this aggration considers deleted things.
     * @return the builder.
     */
    AggregationBuilder withDeletedThings(boolean withDeletedThings);

    /**
     * Marks the aggregation as sudo which means that authorization subjects will not be considered when performing
     * the search.
     *
     * @param sudo if {@code true} this aggration ignores authorization subjects for filtering.
     * @return the builder.
     */
    AggregationBuilder sudo(boolean sudo);

    /**
     * Builds the final PolicyRestrictedSearchAggregation.
     *
     * @return the built aggregation.
     */
    PolicyRestrictedSearchAggregation build();

}
