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

import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.EqPredicateImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FeaturePropertyExpressionImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Tests Bson generators of {@link FeaturePropertyExpressionImpl}.
 */
public final class FeaturePropertyExpressionBsonTest {

    private static final String KNOWN_PROPERTY = "knownFieldName/a";
    private static final String KNOWN_VALUE = "knownValue";
    private static final Predicate KNOWN_PREDICATE = new EqPredicateImpl(KNOWN_VALUE);

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithNullValue() {
        new FeaturePropertyExpressionImpl(null);
    }

    /** */
    @Test
    public void constructValid() {
        final FeaturePropertyExpressionImpl expression = new FeaturePropertyExpressionImpl(KNOWN_PROPERTY);

        Assertions.assertThat(expression).isNotNull();
    }

    /** */
    @Test
    public void getFieldCriteriaBson() {
        final Bson expectedBson = Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters
                .and(Filters.eq(
                        PersistenceConstants.FIELD_INTERNAL_KEY,
                        PersistenceConstants.FIELD_FEATURE_PROPERTIES_PREFIX_WITH_ENDING_SLASH + KNOWN_PROPERTY),
                        Filters.eq(PersistenceConstants.FIELD_INTERNAL_VALUE, KNOWN_VALUE)));

        final FeaturePropertyExpressionImpl expression = new FeaturePropertyExpressionImpl(KNOWN_PROPERTY);

        final Bson createdBson = GetFilterBsonVisitor.apply(expression,
                KNOWN_PREDICATE.accept(CreateBsonPredicateVisitor.getInstance()));

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

}
