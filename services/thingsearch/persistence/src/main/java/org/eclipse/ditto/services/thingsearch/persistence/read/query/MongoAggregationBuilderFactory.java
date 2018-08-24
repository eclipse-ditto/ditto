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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.LimitsConfigReader;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilderFactory;

/**
 * Mongo implementation for {@link AggregationBuilderFactory}.
 */
@Immutable
public final class MongoAggregationBuilderFactory implements AggregationBuilderFactory {

    private final LimitsConfigReader limitsConfigReader;

    public MongoAggregationBuilderFactory(final LimitsConfigReader limitsConfigReader) {
        this.limitsConfigReader = limitsConfigReader;
    }

    public static AggregationBuilder newBuilder(final LimitsConfigReader limitsConfigReader) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(limitsConfigReader);
    }

    @Override
    public AggregationBuilder newBuilder(final Criteria criteria) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(limitsConfigReader).filterCriteria(criteria);
    }

    @Override
    public AggregationBuilder newCountBuilder(final Criteria criteria) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(limitsConfigReader).filterCriteria(criteria).count(true);
    }

}
