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

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;

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

}
