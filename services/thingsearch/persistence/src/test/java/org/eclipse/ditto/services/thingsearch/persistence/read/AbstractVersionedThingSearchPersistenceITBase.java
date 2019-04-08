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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.eclipse.ditto.services.thingsearch.common.model.ResultList;

/**
 * Abstract base class for search persistence tests parameterized with version.
 */
@RunWith(Parameterized.class)
public abstract class AbstractVersionedThingSearchPersistenceITBase extends AbstractReadPersistenceITBase {

    public static List<JsonSchemaVersion> apiVersions() {
        return Arrays.asList(JsonSchemaVersion.values());
    }

    @Parameterized.Parameters(name = "v{0} - {1}")
    public static List<Object[]> versionAndQueryClassParameters() {
        return apiVersions()
                .stream()
                .map(apiVersion -> new Object[]{apiVersion, Query.class.getSimpleName()})
                .collect(Collectors.toList());
    }

    @Parameterized.Parameter
    public JsonSchemaVersion testedApiVersion;

    @Parameterized.Parameter(1)
    public String queryClass;


    @Before
    public void before() {
        super.before();
        if (isV1()) {
            createTestDataV1();
        } else {
            createTestDataV2();
        }
    }

    abstract void createTestDataV1();

    abstract void createTestDataV2();

    @Override
    JsonSchemaVersion getVersion() {
        return testedApiVersion;
    }

    /**
     * Creates a {@link Query} for the given {@link Criteria}.
     */
    private Query query(final Criteria criteria) {
        return qbf.newBuilder(criteria).build();
    }

    ResultList<String> executeVersionedQuery(final Criteria criteria) {
        return executeVersionedQuery(this::query, this::findAll, criteria);
    }

    /**
     * Execute a versioned query based on either via {@link Query}.
     *
     * @param <R> The result type.
     * @param queryMapper The mapper for creating {@link Query} from {@link Criteria}.
     * @param queryFn The function for getting the search result when applying a {@link Query}.
     * @param criteria The {@link Criteria} to search for.
     * @return The result of the search operation.
     */
    <R> R executeVersionedQuery(final Function<Criteria, Query> queryMapper,
            final Function<Query, R> queryFn,
            final Criteria criteria) {
        if (queryClass.equals(Query.class.getSimpleName())) {
            return queryFn.apply(queryMapper.apply(criteria));
        } else {
            throw new IllegalStateException("should never happen");
        }
    }
}
