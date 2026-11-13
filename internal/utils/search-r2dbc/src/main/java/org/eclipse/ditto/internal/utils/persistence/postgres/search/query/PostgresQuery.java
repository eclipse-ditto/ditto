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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;

/**
 * The PostgreSQL implementation of the neutral {@link Query} — a plain data holder (criteria + sort options + limit +
 * skip), the structural twin of {@code MongoQuery} with the BSON coupling removed. Where {@code MongoQuery} exposes
 * {@code getSortOptionsAsBson()}, this exposes {@link #getSortClause()} — the backend-specific extra D4's read
 * persistence obtains via a {@code Query → PostgresQuery} downcast (mirroring {@code MongoThingsSearchPersistence}'s
 * downcast to {@code MongoQuery}).
 */
@Immutable
public final class PostgresQuery implements Query {

    private final Criteria criteria;
    private final List<SortOption> sortOptions;
    private final int limit;
    private final int skip;

    /**
     * @param criteria the query criteria.
     * @param sortOptions the (already thing-id-truncated) sort options.
     * @param limit the result limit.
     * @param skip the result skip.
     */
    public PostgresQuery(final Criteria criteria, final List<SortOption> sortOptions, final int limit, final int skip) {
        this.criteria = Objects.requireNonNull(criteria, "criteria");
        this.sortOptions = Collections.unmodifiableList(new ArrayList<>(sortOptions));
        this.limit = limit;
        this.skip = skip;
    }

    @Override
    public Criteria getCriteria() {
        return criteria;
    }

    @Override
    public List<SortOption> getSortOptions() {
        return sortOptions;
    }

    @Override
    public int getLimit() {
        return limit;
    }

    @Override
    public int getSkip() {
        return skip;
    }

    @Override
    public Query withCriteria(final Criteria newCriteria) {
        return new PostgresQuery(newCriteria, sortOptions, limit, skip);
    }

    /**
     * @return the §3.5 sort/paging translation of this query's sort options — the sort laterals, {@code ORDER BY}
     * assembly, and cursor-value projection D4's {@code findAll} SELECT uses.
     */
    public PostgresSortClause getSortClause() {
        return PostgresSortClause.of(sortOptions);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PostgresQuery that = (PostgresQuery) o;
        return limit == that.limit && skip == that.skip
                && Objects.equals(criteria, that.criteria)
                && Objects.equals(sortOptions, that.sortOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criteria, sortOptions, limit, skip);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " ["
                + "criteria=" + criteria
                + ", sortOptions=" + sortOptions
                + ", limit=" + limit
                + ", skip=" + skip
                + "]";
    }
}
