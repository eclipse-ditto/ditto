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
package org.eclipse.ditto.thingsearch.service.persistence.read.query;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;


/**
 * Mongo implementation for {@link QueryBuilderFactory}.
 */
@Immutable
public final class MongoQueryBuilderFactory implements QueryBuilderFactory {

    private final LimitsConfig limitsConfig;

    public MongoQueryBuilderFactory(final LimitsConfig limitsConfig) {
        this.limitsConfig = limitsConfig;
    }

    @Override
    public QueryBuilder newBuilder(final Criteria criteria) {
        checkCriteria(criteria);
        return MongoQueryBuilder.limited(criteria,
                limitsConfig.getThingsSearchMaxPageSize(), limitsConfig.getThingsSearchDefaultPageSize());
    }

    @Override
    public QueryBuilder newUnlimitedBuilder(final Criteria criteria) {
        checkCriteria(criteria);
        return MongoQueryBuilder.unlimited(criteria);
    }

    private static void checkCriteria(final Criteria criteria) {
        checkNotNull(criteria, "criteria");
    }

}
