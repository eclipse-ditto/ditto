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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;

import com.mongodb.client.model.Sorts;

/**
 * Mongo implementation for {@link Query}.
 */
@Immutable
public final class MongoQuery implements Query {

    private final Criteria criteria;
    private final List<SortOption> sortOptions;
    private final int limit;
    private final int skip;

    @Nullable
    private final String nextPageKey;

    /**
     * Constructor.
     *
     * @param criteria the criteria
     * @param sortOptions the SortOptions
     * @param limit the limit param
     * @param skip the skip param
     */
    public MongoQuery(final Criteria criteria,
            final List<SortOption> sortOptions,
            final int limit,
            final int skip,
            @Nullable final String nextPageKey) {


        this.criteria = checkNotNull(criteria, "criterion");
        this.sortOptions = Collections.unmodifiableList(new ArrayList<>(sortOptions));
        this.limit = limit;
        this.skip = skip;
        this.nextPageKey = nextPageKey;
    }

    /**
     * Constructor.
     *
     * @param criteria the criteria
     * @param sortOptions the SortOptions
     * @param limit the limit param
     * @param skip the skip param
     */
    public MongoQuery(final Criteria criteria,
            final List<SortOption> sortOptions,
            final int limit,
            final int skip) {

        this(criteria, sortOptions, limit, skip, null);
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
    public Optional<String> getNextPageKey() {
        return Optional.ofNullable(nextPageKey);
    }

    /**
     * Gets the SortOptions as {@link Bson}.
     *
     * @return the Bson
     */
    public Bson getSortOptionsAsBson() {
        final List<Bson> sorts = new ArrayList<>();

        for (final SortOption sortOption : sortOptions) {
            final SortDirection sortDirection = sortOption.getSortDirection();

            final SortFieldExpression sortExpression = sortOption.getSortExpression();

            final List<Bson> currentSorts = GetSortBsonVisitor.apply(sortExpression, sortDirection);
            sorts.addAll(currentSorts);
        }

        return Sorts.orderBy(sorts);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoQuery that = (MongoQuery) o;
        return limit == that.limit &&
                skip == that.skip &&
                Objects.equals(criteria, that.criteria) &&
                Objects.equals(sortOptions, that.sortOptions) &&
                Objects.equals(nextPageKey, that.nextPageKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(criteria, sortOptions, limit, skip, nextPageKey);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "criteria=" + criteria +
                ", sortOptions=" + sortOptions +
                ", limit=" + limit +
                ", skip=" + skip +
                ", nextPageKey=" + nextPageKey +
                "]";
    }

}
