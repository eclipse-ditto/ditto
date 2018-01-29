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
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetExistsBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FeatureExpressionImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Tests Bson generators of {@link FeatureExpressionImpl}.
 */
public final class FeatureExpressionBsonTest {

    private static final String KNOWN_FEATURE_ID = "feature1";

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithNullValue() {
        new FeatureExpressionImpl(null);
    }

    /** */
    @Test
    public void constructValid() {
        final FeatureExpressionImpl expression = new FeatureExpressionImpl(KNOWN_FEATURE_ID);

        Assertions.assertThat(expression).isNotNull();
    }

    /** */
    @Test
    public void getFieldCriteriaBson() {
        final Bson expectedBson = Filters.eq(
                PersistenceConstants.FIELD_INTERNAL + "." + PersistenceConstants.FIELD_INTERNAL_FEATURE_ID,
                KNOWN_FEATURE_ID);

        final FeatureExpressionImpl expression = new FeatureExpressionImpl(KNOWN_FEATURE_ID);

        final Bson createdBson = GetExistsBsonVisitor.apply(expression);

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

}
