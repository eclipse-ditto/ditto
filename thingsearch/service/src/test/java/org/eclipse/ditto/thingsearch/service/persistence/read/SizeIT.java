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
import java.util.stream.Collectors;

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
 * Tests for the size option of search persistence.
 */
public final class SizeIT extends AbstractReadPersistenceITBase {

    private static final int KNOWN_SIZE = 2;
    private static final ThingId THING_ID1 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId1");
    private static final ThingId THING_ID2 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId2");
    private static final ThingId THING_ID3 = TestConstants.thingId(TestConstants.Thing.NAMESPACE, "thingId3");

    private static final List<ThingId> THING_IDS = Arrays.asList(THING_ID1, THING_ID2, THING_ID3);

    private static DefaultLimitsConfig limitsConfig;

    private int maxPageSizeFromConfig;
    private int defaultPageSizeFromConfig;

    private final ThingsFieldExpressionFactory eft = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

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
    public void resultWithItemsCountEqualToSize() {
        // prepare
        insertThings(THING_IDS);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.size(KNOWN_SIZE));

        // verify
        final List<ThingId> expectedList = Arrays.asList(THING_ID1, THING_ID2);
        assertResult(result, expectedList);
    }

    @Test
    public void resultWithItemsCountLessThanSize() {
        // prepare
        final List<ThingId> oneThingList = Collections.singletonList(THING_ID1);
        insertThings(oneThingList);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.size(KNOWN_SIZE));

        // verify
        assertResult(result, oneThingList);
    }

    @Test
    public void defaultSizeValue() {
        // prepare
        final int moreThanDefaultPageSize = 30;
        final long totalThingsCount = defaultPageSizeFromConfig + moreThanDefaultPageSize;
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
        assertResult(result, expectedList);
    }

    @Test(expected = IllegalArgumentException.class)
    public void sizeValueExceedsMaximum() {
        executeVersionedQueryWithChangeOptions(query -> query.size(maxPageSizeFromConfig + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sizeValueBelowMinimum() {
        executeVersionedQueryWithChangeOptions(query -> query.size(-1));
    }

    @Test
    public void resultSkipsDeletedItems() {
        // prepare
        insertThings(THING_IDS);

        // delete Thing from first page
        final int size = 3;
        final ThingId thingToDelete = THING_IDS.get(size - 1);
        deleteThing(thingToDelete, 1L, PolicyId.of(thingToDelete.toString()), 1L);

        final ResultList<ThingId> result = executeVersionedQueryWithChangeOptions(query -> query.size(size));

        // verify
        final List<ThingId> expectedList = THING_IDS.stream()
                .filter(id -> !thingToDelete.equals(id))
                .limit(size)
                .collect(Collectors.toList());

        assertResult(result, expectedList);
    }

    private static void assertResult(final ResultList<ThingId> actualResult, final List<ThingId> expectedList) {

        assertThat(actualResult).containsOnly(expectedList.toArray(new ThingId[0]));
        assertThat(actualResult).hasSize(expectedList.size());
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
