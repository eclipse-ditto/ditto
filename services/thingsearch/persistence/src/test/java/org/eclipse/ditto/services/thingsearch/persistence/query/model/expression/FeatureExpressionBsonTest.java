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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.expression;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.expression.FeatureExpressionImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetExistsBsonVisitor;

/**
 * Tests Bson generators of {@link FeatureExpressionImpl}.
 */
public final class FeatureExpressionBsonTest {

    private static final String KNOWN_FEATURE_ID = "feature1";

    @Test(expected = NullPointerException.class)
    public void constructWithNullValue() {
        new FeatureExpressionImpl(null);
    }

    @Test
    public void constructValid() {
        final FeatureExpressionImpl expression = new FeatureExpressionImpl(KNOWN_FEATURE_ID);

        Assertions.assertThat(expression).isNotNull();
    }

    @Test
    public void getFieldCriteriaBson() {
        final List<String> subjectIds = Arrays.asList("subject:alpha", "subject:beta");

        final Bson expectedBson = Filters.elemMatch(FIELD_INTERNAL, Filters.and(
                Filters.regex(FIELD_INTERNAL_KEY, "^/features/" + KNOWN_FEATURE_ID + "(/|\\z)"),
                Filters.and(
                        Filters.in(FIELD_GRANTED, subjectIds),
                        Filters.nin(FIELD_REVOKED, subjectIds)
                )));

        final FeatureExpressionImpl expression = new FeatureExpressionImpl(KNOWN_FEATURE_ID);

        final Bson createdBson = GetExistsBsonVisitor.apply(expression, subjectIds);

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

}
