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
package org.eclipse.ditto.model.query;

import org.eclipse.ditto.model.query.criteria.Criteria;

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
