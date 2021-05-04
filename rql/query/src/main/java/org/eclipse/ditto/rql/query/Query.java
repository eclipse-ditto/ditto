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

import org.eclipse.ditto.rql.query.criteria.Criteria;

/**
 * Defines a query.
 */
public interface Query {

    /**
     * @return the Criteria
     */
    Criteria getCriteria();

    /**
     * @return the SortOptions
     */
    List<SortOption> getSortOptions();

    /**
     * @return the number of results to which this query is limited.
     */
    int getLimit();

    /**
     * @return the number of results which are discarded from the beginning of this query.
     */
    int getSkip();

    /**
     * Replace criteria of this query by another.
     *
     * @param criteria the new criteria.
     * @return a copy of this query with the new criteria.
     * @since 1.1.0
     */
    Query withCriteria(Criteria criteria);
}
