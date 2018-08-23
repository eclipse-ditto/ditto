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

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.AggregationBuilderFactory;

import com.typesafe.config.Config;

/**
 * Mongo implementation for {@link AggregationBuilderFactory}.
 */
@Immutable
public final class MongoAggregationBuilderFactory implements AggregationBuilderFactory {

    private final Config config;

    public MongoAggregationBuilderFactory(final Config config) {
        this.config = config;
    }

    public static AggregationBuilder newBuilder(final Config config) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(config);
    }

    @Override
    public AggregationBuilder newBuilder(final Criteria criteria) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(config).filterCriteria(criteria);
    }

    @Override
    public AggregationBuilder newCountBuilder(final Criteria criteria) {
        return new PolicyRestrictedMongoSearchAggregation.Builder(config).filterCriteria(criteria).count(true);
    }

}
