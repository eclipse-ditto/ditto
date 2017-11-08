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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryBuilder;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryConstants;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;

/**
 * Mongo implementation for {@link QueryBuilder}.
 */
@NotThreadSafe
final class MongoQueryBuilder implements QueryBuilder {

    static final int DEFAULT_SKIP = 0;

    /**
     * The default value for the limit parameter, if we have a unlimited query (count).
     */
    private static final int DEFAULT_LIMIT_UNLIMITED = 0;

    /**
     * The max value for the limit parameter, if we have a unlimited query (count).
     */
    private static final int MAX_LIMIT_UNLIMITED = Integer.MAX_VALUE;

    private final Criteria criteria;
    private final int maxLimit;
    private int limit;
    private int skip;
    private List<SortOption> sortOptions;

    private MongoQueryBuilder(final Criteria criteria, final int maxLimit, final int defaultLimit) {
        this.criteria = checkNotNull(criteria, "criteria");
        this.maxLimit = maxLimit;
        limit = defaultLimit;
        skip = DEFAULT_SKIP;
        sortOptions = new ArrayList<>();
    }

    /**
     * Creates a builder for a limited query ("standard" search).
     *
     * @param criteria the query criteria.
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    public static MongoQueryBuilder limited(final Criteria criteria) {
        return new MongoQueryBuilder(criteria, QueryConstants.MAX_LIMIT, QueryConstants.DEFAULT_LIMIT);
    }

    /**
     * Creates a builder for a unlimited query (count).
     *
     * @param criteria the query criteria.
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    public static MongoQueryBuilder unlimited(final Criteria criteria) {
        return new MongoQueryBuilder(criteria, MAX_LIMIT_UNLIMITED, DEFAULT_LIMIT_UNLIMITED);
    }

    @Override
    public QueryBuilder sort(final List<SortOption> sortOptions) {
        this.sortOptions = checkNotNull(sortOptions, "sort options");
        return this;
    }

    @Override
    public QueryBuilder limit(final long n) {
        limit = Validator.checkLimit(n, maxLimit);
        return this;
    }

    @Override
    public QueryBuilder skip(final long n) {
        skip = Validator.checkSkip(n);
        return this;
    }

    @Override
    public Query build() {
        return new MongoQuery(criteria, sortOptions, limit, skip);
    }

}
