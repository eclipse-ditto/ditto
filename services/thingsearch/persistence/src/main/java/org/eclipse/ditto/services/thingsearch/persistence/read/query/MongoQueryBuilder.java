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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;

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

    private static final SortFieldExpression ID_SORT_FIELD_EXPRESSION = new SimpleFieldExpressionImpl(FIELD_ID);

    private static final List<SortOption> DEFAULT_SORT_OPTIONS =
            Collections.singletonList(new SortOption(ID_SORT_FIELD_EXPRESSION, SortDirection.ASC));

    private final Criteria criteria;
    private final int maxLimit;
    private int limit;
    private int skip;
    private List<SortOption> sortOptions;

    @Nullable
    private String cursor;

    private MongoQueryBuilder(final Criteria criteria, final int maxLimit, final int defaultLimit) {

        this.criteria = checkNotNull(criteria, "criteria");
        this.maxLimit = maxLimit;
        limit = defaultLimit;
        skip = DEFAULT_SKIP;
        sortOptions = DEFAULT_SORT_OPTIONS;
    }

    /**
     * Creates a builder for a limited query ("standard" search).
     *
     * @param criteria the query criteria.
     * @param maxPageSize the max page size which should be able
     * @param defaultPageSize the default page size which should be applied when no explicit one is specified
     * @throws NullPointerException if {@code criteria} is {@code null}.
     */
    public static MongoQueryBuilder limited(final Criteria criteria, final int maxPageSize, final int defaultPageSize) {
        return new MongoQueryBuilder(criteria, maxPageSize, defaultPageSize);
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
        checkNotNull(sortOptions, "sort options");
        if (sortOptions.stream().map(SortOption::getSortExpression).anyMatch(ID_SORT_FIELD_EXPRESSION::equals)) {
            this.sortOptions = sortOptions;
        } else {
            final List<SortOption> options = new ArrayList<>(sortOptions.size() + DEFAULT_SORT_OPTIONS.size());
            options.addAll(sortOptions);
            options.addAll(DEFAULT_SORT_OPTIONS);
            this.sortOptions = options;
        }
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
    public QueryBuilder cursor(final String cursor) {
        this.cursor = cursor;
        return this;
    }

    @Override
    public Query build() {
        return new MongoQuery(criteria, sortOptions, limit, skip, cursor);
    }

}
