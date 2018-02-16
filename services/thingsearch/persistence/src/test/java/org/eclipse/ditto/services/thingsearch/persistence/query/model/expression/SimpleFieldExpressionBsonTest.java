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
import java.util.List;

import org.assertj.core.api.Assertions;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.EqPredicateImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.SimpleFieldExpressionImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.query.SortDirection;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Sorts;

/**
 * Tests Bson generators of {@link SimpleFieldExpressionImpl}.
 */
public final class SimpleFieldExpressionBsonTest {

    private static final String KNOWN_FIELD_NAME = "knownFieldName";
    private static final String KNOWN_VALUE = "knownValue";
    private static final Predicate KNOWN_PREDICATE = new EqPredicateImpl(KNOWN_VALUE);

    /** */
    @Test(expected = NullPointerException.class)
    public void constructWithNullValue() {
        new SimpleFieldExpressionImpl(null);
    }

    /** */
    @Test
    public void constructValid() {
        final SimpleFieldExpressionImpl expression = new SimpleFieldExpressionImpl(KNOWN_FIELD_NAME);

        Assertions.assertThat(expression).isNotNull();
    }

    /** */
    @Test
    public void getFieldCriteriaBson() {
        final Bson expectedBson = CreateBsonPredicateVisitor.apply(KNOWN_PREDICATE, KNOWN_FIELD_NAME);

        final SimpleFieldExpressionImpl expression = new SimpleFieldExpressionImpl(KNOWN_FIELD_NAME);

        final Bson createdBson = GetFilterBsonVisitor.apply(expression,
                KNOWN_PREDICATE.accept(CreateBsonPredicateVisitor.getInstance()));

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

    /** */
    @Test
    public void getSortBson() {
        final List<BsonDocument> expectedSortBsonDocs =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocuments(
                        Collections.singletonList(Sorts.descending(KNOWN_FIELD_NAME)));

        final SimpleFieldExpressionImpl expression = new SimpleFieldExpressionImpl(KNOWN_FIELD_NAME);
        final List<Bson> actualSorts = GetSortBsonVisitor.apply(expression, SortDirection.DESC);
        final Collection<BsonDocument> actualSortBsonDocs =
                org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocuments(actualSorts);

        BsonAssertions.assertThat(actualSortBsonDocs).isEqualTo(expectedSortBsonDocs);
    }

}
