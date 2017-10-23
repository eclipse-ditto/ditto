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

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;

/**
 * Factory for creating a {@link QueryBuilder}.
 */
public interface QueryBuilderFactory {

    /**
     * Creates a new {@link QueryBuilder}.
     *
     * @param criteria the query criteria.
     * @return the builder.
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    QueryBuilder newBuilder(Criteria criteria);

    /**
     * Creates a new {@link QueryBuilder} without a default limit. Be careful to use this builder as queries created by
     * it could run for a very long time.
     *
     * @param criteria the query criteria.
     * @return the builder.
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    QueryBuilder newUnlimitedBuilder(Criteria criteria);

}
