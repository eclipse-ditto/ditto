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
package org.eclipse.ditto.services.thingsearch.persistence.read.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.QueryConstants;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortOption;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.client.model.Sorts;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link MongoQuery}.
 */
public final class MongoQueryTest {

    private static final Criteria KNOWN_CRIT = mock(Criteria.class);
    private static final ThingsFieldExpressionFactory EFT = new ThingsFieldExpressionFactoryImpl();
    private List<SortOption> knownSortOptions;
    private Bson knownSortOptionsExpectedBson;

    /** */
    @Before
    public void before() {
        final SortFieldExpression sortExp1 = EFT.sortByThingId();
        final SortFieldExpression sortExp2 = EFT.sortByAttribute("test");
        knownSortOptions =
                Arrays.asList(new SortOption(sortExp1, SortDirection.ASC),
                        new SortOption(sortExp2, SortDirection.DESC));
        final String thingIdFieldName = ((SimpleFieldExpressionImpl) EFT.sortByThingId()).getFieldName();
        final String attributeFieldName = "attributes.test";
        knownSortOptionsExpectedBson =
                Sorts.orderBy(Arrays.asList(Sorts.ascending(thingIdFieldName), Sorts.descending(attributeFieldName)));
    }

    /** */
    @Test
    public void hashcodeAndEquals() {
        EqualsVerifier.forClass(MongoQuery.class).verify();
    }

    /** */
    @Test
    public void immutability() {
        assertInstancesOf(MongoQuery.class, areImmutable(),
                provided(Criteria.class, SortOption.class).isAlsoImmutable());
    }

    /** */
    @Test
    public void criteriaIsCorrectlySet() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, knownSortOptions, QueryConstants.DEFAULT_LIMIT,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
    }

    /** */
    @Test
    public void emptySortOptions() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, Collections.emptyList(), QueryConstants.DEFAULT_LIMIT,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
        assertThat(query.getSortOptions()).isEmpty();
        assertBson(new BsonDocument(), query.getSortOptionsAsBson());
    }

    private static void assertBson(final Bson expected, final Bson actual) {
        final BsonDocument expectedDoc =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocument(expected);
        final BsonDocument actualDoc =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocument(actual);
        assertThat(actualDoc).isEqualTo(expectedDoc);
    }

    /** */
    @Test
    public void sortOptionsAreCorrectlySet() {
        final MongoQuery query = new MongoQuery(KNOWN_CRIT, knownSortOptions, QueryConstants.DEFAULT_LIMIT,
                MongoQueryBuilder.DEFAULT_SKIP);

        assertThat(query.getCriteria()).isEqualTo(KNOWN_CRIT);
        assertThat(query.getSortOptions()).isEqualTo(knownSortOptions);
        assertBson(knownSortOptionsExpectedBson, query.getSortOptionsAsBson());
    }

}
