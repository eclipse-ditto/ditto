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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.services.base.config.LimitsConfigReader;

/**
 * Mongo implementation for {@link QueryBuilderFactory}.
 */
@Immutable
public final class MongoQueryBuilderFactory implements QueryBuilderFactory {

    private final LimitsConfigReader limitsConfigReader;

    public MongoQueryBuilderFactory(final LimitsConfigReader limitsConfigReader) {

        this.limitsConfigReader = limitsConfigReader;
    }

    @Override
    public QueryBuilder newBuilder(final Criteria criteria) {
        checkCriteria(criteria);
        return MongoQueryBuilder.limited(criteria,
                limitsConfigReader.thingsSearchMaxPageSize(), limitsConfigReader.thingsSearchDefaultPageSize());
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
