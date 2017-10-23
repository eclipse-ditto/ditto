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

import java.util.List;

/**
 * Defines a Builder for a {@link Query}.
 */
public interface QueryBuilder {

    /**
     * Specify SortOptions. Overwrites existing SortOptions.
     *
     * @param sortOptions the SortOptions.
     * @return this builder.
     * @throws NullPointerException if {@code sortOptions} is {@code null}.
     */
    QueryBuilder sort(List<SortOption> sortOptions);

    /**
     * Limits the number of elements returned by the query.
     *
     * @param n the (maximum) number of elements to be returned
     * @return this builder
     */
    QueryBuilder limit(long n);

    /**
     * Discards the given number of elements at the beginning of the query result.
     *
     * @param n the number of elements to be skipped
     * @return this builder
     */
    QueryBuilder skip(long n);

    /**
     * Builds the Query.
     *
     * @return the Query
     */
    Query build();

}
