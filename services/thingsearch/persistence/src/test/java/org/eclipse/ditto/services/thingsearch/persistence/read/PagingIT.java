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
package org.eclipse.ditto.services.thingsearch.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.Thing.NAMESPACE;
import static org.eclipse.ditto.services.thingsearch.persistence.TestConstants.thingId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.model.query.QueryBuilder;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.SortOption;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.base.config.DittoLimitsConfigReader;
import org.eclipse.ditto.services.base.config.LimitsConfigReader;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Tests for the paging functionality of search persistence.
 */
public final class PagingIT extends AbstractVersionedThingSearchPersistenceITBase {

    private static final int KNOWN_LIMIT = 2;
    private static final String THING_ID1 = thingId(NAMESPACE, "thingId1");
    private static final String THING_ID2 = thingId(NAMESPACE, "thingId2");
    private static final String THING_ID3 = thingId(NAMESPACE, "thingId3");
    private static final String THING_ID4 = thingId(NAMESPACE, "thingId4");
    private static final String THING_ID5 = thingId(NAMESPACE, "thingId5");
    private static final String THING_ID6 = thingId(NAMESPACE, "thingId6");
    private static final List<String> THING_IDS = Arrays.asList(THING_ID1, THING_ID2, THING_ID3, THING_ID4, THING_ID5,
            THING_ID6);
    private final ThingsFieldExpressionFactory eft = new ThingsFieldExpressionFactoryImpl();

    private int maxPageSizeFromConfig;
    private int defaultPageSizeFromConfig;

    /** */
    @Before
    public void setUp() {
        final LimitsConfigReader limitsConfigReader = DittoLimitsConfigReader.fromRawConfig(ConfigFactory.load("test"));
        maxPageSizeFromConfig = limitsConfigReader.thingsSearchMaxPageSize();
        defaultPageSizeFromConfig = limitsConfigReader.thingsSearchDefaultPageSize();
    }

    @Override
    void createTestDataV1() {
        // test-data are created in tests
    }

    @Override
    void createTestDataV2() {
        // test-data are created in tests
    }

    /** */
    @Test
    public void pageWithItemsCountLessThanLimit() {
        // prepare
        final List<String> oneThingList = Collections.singletonList(THING_ID1);
        insertThings(oneThingList);

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query.limit(KNOWN_LIMIT),
                aggregation -> aggregation.limit(KNOWN_LIMIT));

        // verify
        assertPaging(result, oneThingList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void pageWithItemsCountEqualToLimit() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query.limit(KNOWN_LIMIT),
                aggregation -> aggregation.limit(KNOWN_LIMIT));

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID1, THING_ID2);
        assertPaging(result, expectedList, KNOWN_LIMIT);
    }

    /** */
    @Test
    public void pageWithSkipAndLimitLessThanTotalItems() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT),
                aggregation -> aggregation.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT));

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID3, THING_ID4);
        assertPaging(result, expectedList, KNOWN_LIMIT * 2);
    }

    /** */
    @Test
    public void lastPageWithItemsCountLessThanLimit() {
        // prepare
        insertThings(THING_IDS.subList(0, THING_IDS.size() - 1));

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2),
                aggregation -> aggregation.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2));

        // verify
        final List<String> expectedList = Collections.singletonList(THING_ID5);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void lastPageWithItemsCountEqualToLimit() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2),
                aggregation -> aggregation.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2));

        // verify
        final List<String> expectedList = Arrays.asList(THING_ID5, THING_ID6);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    /** */
    @Test
    public void defaultLimitValue() {
        // prepare
        final int moreThanLimit = 30;
        final long totalThingsCount = defaultPageSizeFromConfig + moreThanLimit;
        final List<String> allThings = new ArrayList<>((int) totalThingsCount);
        for (int i = 0; i < totalThingsCount; i++) {
            final String thingId = thingId(NAMESPACE, "thingId") + String.format("%03d", i);
            persistThing(createThing(thingId));
            allThings.add(thingId);
        }

        final ResultList<String> result = executeVersionedQueryWithChangeOptions(
                query -> query,
                aggregation -> aggregation);

        // verify
        final List<String> expectedList = allThings.subList(0, defaultPageSizeFromConfig);
        assertPaging(result, expectedList, defaultPageSizeFromConfig);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void limitValueExceedsMaximum() {
        executeVersionedQueryWithChangeOptions(
                query -> query.limit(maxPageSizeFromConfig + 1),
                aggregation -> aggregation.limit(maxPageSizeFromConfig + 1));
    }

    private static void assertPaging(final ResultList<String> actualResult, final List<String> expectedList,
            final long expectedNextPageOffset) {

        assertThat(actualResult).containsOnly(expectedList.toArray(new String[0]));
        assertThat(actualResult.nextPageOffset()).isEqualTo(expectedNextPageOffset);
    }

    private void insertThings(final List<String> thingIds) {
        shuffleAndPersist(createThings(thingIds));
    }

    private void shuffleAndPersist(final List<Thing> things) {
        // shuffle the documents for more realistic testing
        Collections.shuffle(things);
        things.forEach(this::persistThing);
    }

    private ResultList<String> executeVersionedQueryWithChangeOptions(
            final Function<QueryBuilder, QueryBuilder> queryChanger,
            final Function<AggregationBuilder, AggregationBuilder> aggregationChanger) {
        return executeVersionedQuery(
                (criteria -> queryChanger.apply(qbf.newBuilder(criteria)
                        .sort(Collections.singletonList(new SortOption(eft.sortByThingId(), SortDirection.ASC))))
                        .build()),
                (criteria -> aggregationChanger.apply(abf.newBuilder(criteria)
                        .authorizationSubjects(KNOWN_SUBJECTS)).build()),
                this::findAll,
                this::findAll,
                cf.any());

    }

}
