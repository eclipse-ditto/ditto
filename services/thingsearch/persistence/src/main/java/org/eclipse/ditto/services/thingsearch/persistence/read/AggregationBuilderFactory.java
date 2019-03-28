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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import org.eclipse.ditto.model.query.criteria.Criteria;

/**
 * Factory for creating a {@link AggregationBuilder}.
 */
public interface AggregationBuilderFactory {

    /**
     * Creates a new {@link AggregationBuilder}.
     *
     * @param criteria the query criteria
     * @return the builder
     */
    AggregationBuilder newBuilder(Criteria criteria);

    /**
     * Creates a new {@link AggregationBuilder} without a default limit. Be careful to use this builder as queries created by
     * it could run for a very long time.
     *
     * @param criteria the query criteria
     * @return the builder
     */
    AggregationBuilder newCountBuilder(Criteria criteria);

}
