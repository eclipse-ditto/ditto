/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
