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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.DOT;
import static org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants.FIELD_THING;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.BsonUtil;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.SimpleFieldExpression;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.client.model.Sorts;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link MongoQuery}.
 */
public final class MongoQueryTest {

    private static final Criteria KNOWN_CRIT = mock(Criteria.class);

    private static final Map<String, String> SIMPLE_FIELD_MAPPINGS = new HashMap<>();
    static {
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        SIMPLE_FIELD_MAPPINGS.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    private static final ThingsFieldExpressionFactory EFT = ThingsFieldExpressionFactory.of(SIMPLE_FIELD_MAPPINGS);

    private static int defaultPageSizeFromConfig;

    private List<SortOption> knownSortOptions;
    private Bson knownSortOptionsExpectedBson;

    @BeforeClass
    public static void initTestFixture() {
        final Config testConfig = ConfigFactory.load("test");
        final DefaultLimitsConfig limitsConfig =
                DefaultLimitsConfig.of(testConfig.getConfig(ScopedConfig.DITTO_SCOPE));
        defaultPageSizeFromConfig = limitsConfig.getThingsSearchDefaultPageSize();
    }

    @Before
    public void before() {
        final SortFieldExpression sortExp1 = EFT.sortByThingId();
        final SortFieldExpression sortExp2 = EFT.sortByAttribute("test");
        knownSortOptions =
                Arrays.asList(new SortOption(sortExp1, SortDirection.ASC),
                        new SortOption(sortExp2, SortDirection.DESC));
        final String thingIdFieldName = ((SimpleFieldExpression) EFT.sortByThingId()).getFieldName();
        final String attributeFieldName = "attributes.test";
        knownSortOptionsExpectedBson = Sorts.orderBy(Arrays.asList(
                Sorts.ascending(thingIdFieldName),
                Sorts.descending(FIELD_THING + DOT + attributeFieldName)));
    }

    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(MongoQuery.class).verify();
    }

    @Test
    public void immutability() {
        assertInstancesOf(MongoQuery.class, areImmutable(),
                provided(Criteria.class, SortOption.class).isAlsoImmutable());
    }

    @Test
    public void criteriaIsCorrectlySet() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, knownSortOptions, defaultPageSizeFromConfig,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
    }

    @Test
    public void emptySortOptions() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, Collections.emptyList(), defaultPageSizeFromConfig,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
        assertThat(query.getSortOptions()).isEmpty();
        assertBson(new BsonDocument(), query.getSortOptionsAsBson());
    }

    @Test
    public void sortOptionsAreCorrectlySet() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, knownSortOptions, defaultPageSizeFromConfig,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
        assertThat(query.getSortOptions()).isEqualTo(knownSortOptions);
        assertBson(knownSortOptionsExpectedBson, query.getSortOptionsAsBson());
    }

    private static void assertBson(final Bson expected, final Bson actual) {
        final BsonDocument expectedDoc =
                BsonUtil.toBsonDocument(expected);
        final BsonDocument actualDoc =
                BsonUtil.toBsonDocument(actual);

        assertThat(actualDoc).isEqualTo(expectedDoc);
    }

}
