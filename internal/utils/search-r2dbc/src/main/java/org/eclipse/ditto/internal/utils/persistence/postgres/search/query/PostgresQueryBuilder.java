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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.SimpleFieldExpression;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;

/**
 * The PostgreSQL {@link QueryBuilder} — a line-for-line transcription of {@code MongoQueryBuilder} (thingsearch/service)
 * onto the neutral rql-query interfaces, carrying NO Mongo coupling. The paging/limit validation and the default-sort +
 * thing-id-truncation semantics are the backend-independent contract of the search read path, so they are reproduced
 * here verbatim (both backends MUST agree for keyset/cursor correctness).
 * <p>
 * <b>Transcription line-map ({@code MongoQueryBuilder} → here):</b>
 * <ul>
 *   <li>{@code 40}/{@code 45}/{@code 50} (DEFAULT_SKIP, DEFAULT_LIMIT_UNLIMITED, MAX_LIMIT_UNLIMITED) → the three
 *       constants below</li>
 *   <li>{@code 52-56} (ID_SORT_FIELD_EXPRESSION = {@code SimpleFieldExpression.of(FIELD_ID)}, DEFAULT_SORT_OPTIONS =
 *       [{@code _id} ASC]) → {@link #ID_SORT_FIELD_EXPRESSION}/{@link #DEFAULT_SORT_OPTIONS} — the internal id name is
 *       the neutral {@code FieldExpressionUtil.FIELD_ID} ("_id"), the same constant {@code PersistenceConstants.FIELD_ID}
 *       aliases</li>
 *   <li>{@code 81-93} (limited/unlimited factories) → {@link #limited}/{@link #unlimited}</li>
 *   <li>{@code 96-110} (sort: truncate the option list AFTER a thing-id entry, else APPEND the default {@code _id} ASC —
 *       either way the list ends in the thing-id key, the total-order tiebreak) → {@link #sort}</li>
 *   <li>{@code 112-128} (limit/size/skip validation) → {@link #limit}/{@link #size}/{@link #skip} (the {@code Validator}
 *       helper is inlined below — it is a package-private thingsearch/service class, not reachable from this module)</li>
 *   <li>{@code 131-133} (build → the concrete Query) → {@link #build} (a {@link PostgresQuery})</li>
 * </ul>
 */
@NotThreadSafe
final class PostgresQueryBuilder implements QueryBuilder {

    private static final int DEFAULT_SKIP = 0;
    private static final int DEFAULT_LIMIT_UNLIMITED = 0;
    private static final int MAX_LIMIT_UNLIMITED = Integer.MAX_VALUE;

    private static final SortFieldExpression ID_SORT_FIELD_EXPRESSION =
            SimpleFieldExpression.of(FieldExpressionUtil.FIELD_ID);

    private static final List<SortOption> DEFAULT_SORT_OPTIONS =
            Collections.singletonList(new SortOption(ID_SORT_FIELD_EXPRESSION, SortDirection.ASC));

    private static final String LIMIT_PARAM = "limit";
    private static final String SIZE_PARAM = "size";
    private static final String SKIP_PARAM = "skip";

    private final Criteria criteria;
    private final int maxLimit;
    private int limit;
    private int skip;
    private List<SortOption> sortOptions;

    private PostgresQueryBuilder(final Criteria criteria, final int maxLimit, final int defaultLimit) {
        this.criteria = Objects.requireNonNull(criteria, "criteria");
        this.maxLimit = maxLimit;
        this.limit = defaultLimit;
        this.skip = DEFAULT_SKIP;
        this.sortOptions = DEFAULT_SORT_OPTIONS;
    }

    /**
     * @param criteria the query criteria.
     * @param maxPageSize the max page size.
     * @param defaultPageSize the default page size when none is specified.
     * @return a builder for a limited ("standard" search) query.
     */
    static PostgresQueryBuilder limited(final Criteria criteria, final int maxPageSize, final int defaultPageSize) {
        return new PostgresQueryBuilder(criteria, maxPageSize, defaultPageSize);
    }

    /**
     * @param criteria the query criteria.
     * @return a builder for an unlimited (count / streaming) query.
     */
    static PostgresQueryBuilder unlimited(final Criteria criteria) {
        return new PostgresQueryBuilder(criteria, MAX_LIMIT_UNLIMITED, DEFAULT_LIMIT_UNLIMITED);
    }

    @Override
    public QueryBuilder sort(final List<SortOption> newSortOptions) {
        Objects.requireNonNull(newSortOptions, "sort options");
        final OptionalInt thingIdEntry = IntStream.range(0, newSortOptions.size())
                .filter(i -> ID_SORT_FIELD_EXPRESSION.equals(newSortOptions.get(i).getSortExpression()))
                .findFirst();
        if (thingIdEntry.isPresent()) {
            // Truncate AFTER the first thing-id entry: everything past a total-order key is dead weight (and would
            // break keyset paging). The list then ENDS in the thing-id key — the ORDER BY tiebreak.
            this.sortOptions = newSortOptions.subList(0, thingIdEntry.getAsInt() + 1);
        } else {
            // No thing-id key given: APPEND the default _id ASC so every query still has a total order.
            final List<SortOption> options = new ArrayList<>(newSortOptions.size() + DEFAULT_SORT_OPTIONS.size());
            options.addAll(newSortOptions);
            options.addAll(DEFAULT_SORT_OPTIONS);
            this.sortOptions = options;
        }
        return this;
    }

    @Override
    public QueryBuilder limit(final long n) {
        this.limit = checkMaxParamValue(checkMinParamValue(n, LIMIT_PARAM), maxLimit, LIMIT_PARAM);
        return this;
    }

    @Override
    public QueryBuilder size(final long n) {
        this.limit = checkMaxParamValue(checkMinParamValue(n, SIZE_PARAM), maxLimit, SIZE_PARAM);
        return this;
    }

    @Override
    public QueryBuilder skip(final long n) {
        this.skip = checkMaxParamValue(checkMinParamValue(n, SKIP_PARAM), Integer.MAX_VALUE, SKIP_PARAM);
        return this;
    }

    @Override
    public Query build() {
        return new PostgresQuery(criteria, sortOptions, limit, skip);
    }

    // ---- inlined Validator (thingsearch/service Validator, a package-private class not reachable here) -------------

    private static long checkMinParamValue(final long value, final String paramName) {
        if (value < 0) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Parameter <{0}> must be greater than or equal to <0> but it was <{1}>!", paramName, value));
        }
        return value;
    }

    private static int checkMaxParamValue(final long value, final int maxParamValue, final String paramName) {
        if (value > maxParamValue) {
            throw new IllegalArgumentException(MessageFormat.format(
                    "Parameter <{0}> must be less than or equal to <{1}> but it was <{2}>!", paramName, maxParamValue,
                    value));
        }
        return (int) value;
    }
}
