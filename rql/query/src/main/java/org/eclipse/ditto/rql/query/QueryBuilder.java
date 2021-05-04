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
package org.eclipse.ditto.rql.query;

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
     * Limits the number of elements returned by the query.
     *
     * @param n the (maximum) number of elements to be returned
     * @return this builder
     */
    QueryBuilder size(long n);

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
