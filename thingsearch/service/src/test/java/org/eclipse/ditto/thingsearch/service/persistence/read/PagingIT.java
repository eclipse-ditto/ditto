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
package org.eclipse.ditto.thingsearch.service.persistence.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.persistence.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests for the paging functionality of search persistence.
 */
public final class PagingIT extends AbstractReadPersistenceITBase {

    private static final int KNOWN_LIMIT = 2;
    private static final ThingId THING_ID1 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId1");
    private static final ThingId THING_ID2 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId2");
    private static final ThingId THING_ID3 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId3");
    private static final ThingId THING_ID4 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId4");
    private static final ThingId THING_ID5 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId5");
    private static final ThingId THING_ID6 =
            TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId6");
    private static final List<ThingId> THING_IDS =
            Arrays.asList(THING_ID1, THING_ID2, THING_ID3, THING_ID4, THING_ID5, THING_ID6);

    private static DefaultLimitsConfig limitsConfig;

    private final ThingsFieldExpressionFactory eft = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    private int maxPageSizeFromConfig;
    private int defaultPageSizeFromConfig;

    @BeforeClass
    public static void initTestFixture() {
        final Config testConfig = ConfigFactory.load("test");
        limitsConfig = DefaultLimitsConfig.of(testConfig.getConfig(ScopedConfig.DITTO_SCOPE));
    }

    @Before
    public void setUp() {
        maxPageSizeFromConfig = limitsConfig.getThingsSearchMaxPageSize();
        defaultPageSizeFromConfig = limitsConfig.getThingsSearchDefaultPageSize();
    }

    @Test
    public void pageWithItemsCountLessThanLimit() {
        // prepare
        final List<ThingId> oneThingList = Collections.singletonList(THING_ID1);
        insertThings(oneThingList);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.limit(KNOWN_LIMIT));

        // verify
        assertPaging(result, oneThingList, ResultList.NO_NEXT_PAGE);
    }

    @Test
    public void pageWithItemsCountEqualToLimit() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.limit(KNOWN_LIMIT));

        // verify
        final List<ThingId> expectedList = Arrays.asList(THING_ID1, THING_ID2);
        assertPaging(result, expectedList, KNOWN_LIMIT);
    }

    @Test
    public void pageWithSkipAndLimitLessThanTotalItems() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<ThingId> result =
                executeVersionedQueryWithChangeOptions(query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT));

        // verify
        final List<ThingId> expectedList = Arrays.asList(THING_ID3, THING_ID4);
        assertPaging(result, expectedList, KNOWN_LIMIT * 2);
    }

    @Test
    public void lastPageWithItemsCountLessThanLimit() {
        // prepare
        insertThings(THING_IDS.subList(0, THING_IDS.size() - 1));

        final ResultList<ThingId> result =
                executeVersionedQueryWithChangeOptions(query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2));

        // verify
        final List<ThingId> expectedList = Collections.singletonList(THING_ID5);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    @Test
    public void lastPageWithItemsCountEqualToLimit() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<ThingId> result =
                executeVersionedQueryWithChangeOptions(query -> query.limit(KNOWN_LIMIT).skip(KNOWN_LIMIT * 2));

        // verify
        final List<ThingId> expectedList = Arrays.asList(THING_ID5, THING_ID6);
        assertPaging(result, expectedList, ResultList.NO_NEXT_PAGE);
    }

    @Test
    public void defaultLimitValue() {
        // prepare
        final int moreThanLimit = 30;
        final long totalThingsCount = defaultPageSizeFromConfig + moreThanLimit;
        final List<ThingId> allThings = new ArrayList<>((int) totalThingsCount);
        final ThingId baseThingId = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId");
        for (int i = 0; i < totalThingsCount; i++) {
            final ThingId thingId =
                    ThingId.of(baseThingId.getNamespace(), baseThingId.getName() + String.format("%03d", i));
            persistThing(createThing(thingId));
            allThings.add(thingId);
        }

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(Function.identity());

        // verify
        final List<ThingId> expectedList = allThings.subList(0, defaultPageSizeFromConfig);
        assertPaging(result, expectedList, defaultPageSizeFromConfig);
    }

    @Test(expected = IllegalArgumentException.class)
    public void limitValueExceedsMaximum() {
        executeVersionedQueryWithChangeOptions(query -> query.limit(maxPageSizeFromConfig + 1));
    }

    @Test
    public void pageSkipsDeletedItems() {
        // prepare
        insertThings(THING_IDS);

        // delete Thing from first page
        final int limit = 3;
        final ThingId thingToDelete = THING_IDS.get(limit - 1);
        deleteThing(thingToDelete, 1L, PolicyId.of(thingToDelete.toString()), 1L);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.limit(limit));

        // verify
        final List<ThingId> expectedList = THING_IDS.stream()
                .filter(id -> !thingToDelete.equals(id))
                .limit(limit)
                .toList();
        assertPaging(result, expectedList, limit);
    }

    private static void assertPaging(final ResultList<ThingId> actualResult, final List<ThingId> expectedList,
            final long expectedNextPageOffset) {

        assertThat(actualResult).containsOnly(expectedList.toArray(new ThingId[0]));
        assertThat(actualResult.nextPageOffset()).isEqualTo(expectedNextPageOffset);
    }

    private void insertThings(final Collection<ThingId> thingIds) {
        shuffleAndPersist(createThings(thingIds));
    }

    private void shuffleAndPersist(final List<Thing> things) {
        // shuffle the documents for more realistic testing
        Collections.shuffle(things);
        things.forEach(this::persistThing);
    }

    private ResultList<ThingId> executeVersionedQueryWithChangeOptions(
            final Function<QueryBuilder, QueryBuilder> queryChanger) {

        final Query query = queryChanger.apply(qbf.newBuilder(cf.any())
                .sort(Collections.singletonList(new SortOption(eft.sortByThingId(), SortDirection.ASC))))
                .build();

        return findAll(query);
    }

}
