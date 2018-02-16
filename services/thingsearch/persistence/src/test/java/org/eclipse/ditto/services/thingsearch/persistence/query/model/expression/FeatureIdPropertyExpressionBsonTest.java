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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.expression;

import java.util.Collection;
import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.EqPredicateImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FeatureIdPropertyExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

/**
 * Tests Bson generators of {@link FeatureIdPropertyExpressionImpl}.
 */
public final class FeatureIdPropertyExpressionBsonTest {

    private static final String KNOWN_PROPERTY = "knownFieldName/a";
    private static final String KNOWN_FEATURE_ID = "feature1";
    private static final String KNOWN_KEY = "features." + KNOWN_FEATURE_ID + ".properties.knownFieldName.a";
    private static final String KNOWN_VALUE = "knownValue";
    private static final Predicate KNOWN_PREDICATE = new EqPredicateImpl(KNOWN_VALUE);

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithFirstArgNullValue() {
        new FeatureIdPropertyExpressionImpl(null, KNOWN_PROPERTY);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithSecondArgNullValue() {
        new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, null);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithBothNullValue() {
        new FeatureIdPropertyExpressionImpl(null, null);
    }

    /** */
    @Test
    public void constructValid() {
        final FeatureIdPropertyExpressionImpl expression =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_PROPERTY);

        Assertions.assertThat(expression).isNotNull();
    }

    /** */
    @Test
    public void getSortBsonDesc() {
        final FeatureIdPropertyExpressionImpl expression =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_PROPERTY);
        final Collection<Bson> createdBsonList = GetSortBsonVisitor.apply(expression, SortDirection.DESC);

        final Bson expectedBson = Sorts.descending(KNOWN_KEY);

        BsonAssertions.assertThat(createdBsonList).isEqualTo(Collections.singletonList(expectedBson));
    }

    /** */
    @Test
    public void getSortBsonAsc() {
        final FeatureIdPropertyExpressionImpl expression =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_PROPERTY);
        final Collection<Bson> createdBsonList = GetSortBsonVisitor.apply(expression, SortDirection.ASC);

        final Bson expectedBson = Sorts.ascending(KNOWN_KEY);

        BsonAssertions.assertThat(createdBsonList).isEqualTo(Collections.singletonList(expectedBson));
    }

    /** */
    @Test
    public void getFieldCriteriaBsonWithFeatureId() {
        final Bson expectedBson = Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters
                .and(Filters.eq(
                        PersistenceConstants.FIELD_INTERNAL_KEY,
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + KNOWN_PROPERTY),
                        Filters.eq(PersistenceConstants.FIELD_INTERNAL_FEATURE_ID, KNOWN_FEATURE_ID),
                        Filters.eq(PersistenceConstants.FIELD_INTERNAL_VALUE, KNOWN_VALUE)));

        final FeatureIdPropertyExpressionImpl expression =
                new FeatureIdPropertyExpressionImpl(KNOWN_FEATURE_ID, KNOWN_PROPERTY);

        final Bson createdBson = GetFilterBsonVisitor.apply(expression,
                KNOWN_PREDICATE.accept(CreateBsonPredicateVisitor.getInstance()));

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

}
