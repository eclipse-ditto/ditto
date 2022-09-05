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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.SimpleFieldExpression;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;

/**
 * Mongo implementation for {@link QueryBuilder}.
 */
@NotThreadSafe
final class MongoQueryBuilder implements QueryBuilder {

    static final int DEFAULT_SKIP = 0;

    /**
     * The default value for the limit parameter, if we have an unlimited query (count).
     */
    private static final int DEFAULT_LIMIT_UNLIMITED = 0;

    /**
     * The max value for the limit parameter, if we have an unlimited query (count).
     */
    private static final int MAX_LIMIT_UNLIMITED = Integer.MAX_VALUE;

    private static final SortFieldExpression ID_SORT_FIELD_EXPRESSION = SimpleFieldExpression.of(
            PersistenceConstants.FIELD_ID);

    private static final List<SortOption> DEFAULT_SORT_OPTIONS =
            Collections.singletonList(new SortOption(ID_SORT_FIELD_EXPRESSION, SortDirection.ASC));

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
        final OptionalInt thingIdEntry = IntStream.range(0, sortOptions.size())
                .filter(i -> ID_SORT_FIELD_EXPRESSION.equals(sortOptions.get(i).getSortExpression()))
                .findFirst();
        if (thingIdEntry.isPresent()) {
            this.sortOptions = sortOptions.subList(0, thingIdEntry.getAsInt() + 1);
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
    public QueryBuilder size(final long n) {
        limit = Validator.checkSize(n, maxLimit);
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
