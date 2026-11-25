/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.criteria.Criteria;

/**
 * The PostgreSQL {@link QueryBuilderFactory} — the structural twin of {@code MongoQueryBuilderFactory}: it wires the
 * neutral {@link LimitsConfig} page-size bounds into every {@link PostgresQueryBuilder} it hands out. Returned by
 * {@code PostgresSearchPersistenceProvider.queryBuilderFactory(LimitsConfig)}.
 */
@Immutable
public final class PostgresQueryBuilderFactory implements QueryBuilderFactory {

    private final LimitsConfig limitsConfig;

    /**
     * @param limitsConfig the neutral limits config supplying the max/default search page sizes.
     */
    public PostgresQueryBuilderFactory(final LimitsConfig limitsConfig) {
        this.limitsConfig = Objects.requireNonNull(limitsConfig, "limitsConfig");
    }

    @Override
    public QueryBuilder newBuilder(final Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        return PostgresQueryBuilder.limited(criteria,
                limitsConfig.getThingsSearchMaxPageSize(), limitsConfig.getThingsSearchDefaultPageSize());
    }

    @Override
    public QueryBuilder newUnlimitedBuilder(final Criteria criteria) {
        Objects.requireNonNull(criteria, "criteria");
        return PostgresQueryBuilder.unlimited(criteria);
    }
}
