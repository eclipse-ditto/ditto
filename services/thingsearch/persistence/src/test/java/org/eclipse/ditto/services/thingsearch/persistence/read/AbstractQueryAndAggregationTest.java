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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.query.PolicyRestrictedSearchAggregation;
import org.eclipse.ditto.services.thingsearch.querymodel.query.Query;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Abstract class, that creates parameterized tests with version AND query/aggregation classes. The {@link Query} API is
 * only used with {@link org.eclipse.ditto.model.base.json.JsonSchemaVersion#V_1}. The {@link
 * PolicyRestrictedSearchAggregation} API is only used with {@link org.eclipse.ditto.model.base.json.JsonSchemaVersion#V_2}.
 */
@RunWith(Parameterized.class)
public abstract class AbstractQueryAndAggregationTest extends AbstractVersionedThingSearchPersistenceTestBase {

    @Parameterized.Parameters(name = "v{0} - {1}")
    public static List<Object[]> parameters() {
        return apiVersions()
                .stream()
                .map(apiVersion -> {
                    if (JsonSchemaVersion.V_1.equals(apiVersion)) {
                        return new Object[]{apiVersion, Query.class.getSimpleName()};
                    } else if (JsonSchemaVersion.V_2.equals(apiVersion)) {
                        return new Object[]{apiVersion, PolicyRestrictedSearchAggregation.class.getSimpleName()};
                    } else {
                        throw new IllegalStateException("should never happen");
                    }
                })
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter(1)
    public String queryClass;

    ResultList<String> executeVersionedQuery(final Criteria criteria) {
        return executeVersionedQuery(this::query, this::aggregation, this::findAll, this::findAll, criteria);
    }

    /**
     * Creates a {@link Query} for the given {@link Criteria}.
     */
    private Query query(final Criteria criteria) {
        return qbf.newBuilder(criteria).build();
    }

    /**
     * Creates a {@link PolicyRestrictedSearchAggregation} for the given {@link Criteria}.
     */
    private PolicyRestrictedSearchAggregation aggregation(final Criteria criteria) {
        return abf.newBuilder(criteria)
                .authorizationSubjects(KNOWN_SUBJECTS)
                .build();
    }

    /**
     * Execute a versioned query based on either via {@link Query} or {@link PolicyRestrictedSearchAggregation}.
     *
     * @param <R> The result type.
     * @param queryMapper The mapper for creating {@link Query} from {@link Criteria}.
     * @param aggregationMapper The mapper for creating {@link PolicyRestrictedSearchAggregation} from {@link
     * Criteria}.
     * @param queryFn The function for getting the search result when applying a {@link Query}.
     * @param aggregationFn The function for getting the search result when applying a {@link
     * PolicyRestrictedSearchAggregation}.
     * @param criteria The {@link Criteria} to search for.
     * @return The result of the search operation.
     */
    <R> R executeVersionedQuery(final Function<Criteria, Query> queryMapper,
            final Function<Criteria, PolicyRestrictedSearchAggregation> aggregationMapper,
            final Function<Query, R> queryFn,
            final Function<PolicyRestrictedSearchAggregation, R> aggregationFn,
            final Criteria criteria) {
        if (queryClass.equals(Query.class.getSimpleName())) {
            return queryFn.apply(queryMapper.apply(criteria));
        } else if (queryClass.equals(PolicyRestrictedSearchAggregation.class.getSimpleName())) {
            return aggregationFn.apply(aggregationMapper.apply(criteria));
        } else {
            throw new IllegalStateException("should never happen");
        }
    }


}
