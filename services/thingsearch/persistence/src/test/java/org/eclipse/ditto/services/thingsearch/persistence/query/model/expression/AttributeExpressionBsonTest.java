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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.DOT;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_SORTING;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.SortDirection;
import org.eclipse.ditto.model.query.criteria.EqPredicateImpl;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.model.query.expression.AttributeExpressionImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetExistsBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetFilterBsonVisitor;
import org.eclipse.ditto.services.thingsearch.persistence.read.expression.visitors.GetSortBsonVisitor;

/**
 * Tests Bson generators of {@link AttributeExpressionImpl}.
 */
public final class AttributeExpressionBsonTest {

    private static final String KNOWN_KEY = "knownFieldName/a";
    private static final String KNOWN_VALUE = "knownValue";
    private static final Predicate KNOWN_PREDICATE = new EqPredicateImpl(KNOWN_VALUE);

    @Test(expected = NullPointerException.class)
    public void constructWithNullValue() {
        new AttributeExpressionImpl(null);
    }

    @Test
    public void constructValid() {
        final AttributeExpressionImpl expression = new AttributeExpressionImpl(KNOWN_KEY);

        Assertions.assertThat(expression).isNotNull();
    }

    @Test
    public void getExistsBson() {
        final List<String> subjectIds = Arrays.asList("subject:alpha", "subject:beta");

        final Bson expectedBson = Filters.elemMatch(FIELD_INTERNAL, Filters.and(
                Filters.regex(FIELD_INTERNAL_KEY, "^/attributes/" + KNOWN_KEY + "(/|\\z)"),
                Filters.and(
                        Filters.in(FIELD_GRANTED, subjectIds),
                        Filters.nin(FIELD_REVOKED, subjectIds)
                )));

        final AttributeExpressionImpl expression = new AttributeExpressionImpl(KNOWN_KEY);

        final Bson createdBson = GetExistsBsonVisitor.apply(expression, subjectIds);

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

    @Test
    public void getFieldCriteriaBson() {
        final List<String> subjectIds = Arrays.asList("subject:alpha", "subject:beta");

        final Bson expectedBson = Filters.elemMatch(FIELD_INTERNAL, Filters.and(
                Filters.eq(FIELD_INTERNAL_KEY, "/attributes/" + KNOWN_KEY),
                Filters.eq(FIELD_INTERNAL_VALUE, KNOWN_VALUE),
                Filters.and(
                        Filters.in(FIELD_GRANTED, subjectIds),
                        Filters.nin(FIELD_REVOKED, subjectIds)
                )));

        final AttributeExpressionImpl expression = new AttributeExpressionImpl(KNOWN_KEY);
        final Function<String, Bson> predicateCreator =
                KNOWN_PREDICATE.accept(CreateBsonPredicateVisitor.getInstance());

        final Bson createdBson = GetFilterBsonVisitor.apply(expression, predicateCreator, subjectIds);

        BsonAssertions.assertThat(createdBson).isEqualTo(expectedBson);
    }

    @Test
    public void getSortBsonDesc() {
        final AttributeExpressionImpl expression = new AttributeExpressionImpl(KNOWN_KEY);
        final Collection<Bson> createdBsonList = GetSortBsonVisitor.apply(expression, SortDirection.DESC);

        final Bson expectedBson = Sorts.descending(
                FIELD_SORTING + DOT + FIELD_ATTRIBUTES + DOT + KNOWN_KEY.replaceAll("/", "."));

        BsonAssertions.assertThat(createdBsonList).isEqualTo(Collections.singletonList(expectedBson));
    }

    @Test
    public void getSortBsonAsc() {
        final AttributeExpressionImpl expression = new AttributeExpressionImpl(KNOWN_KEY);
        final Collection<Bson> createdBsonList = GetSortBsonVisitor.apply(expression, SortDirection.ASC);

        final Bson expectedBson = Sorts.ascending(
                FIELD_SORTING + DOT + FIELD_ATTRIBUTES + DOT + KNOWN_KEY.replaceAll("/", "."));

        BsonAssertions.assertThat(createdBsonList).isEqualTo(Collections.singletonList(expectedBson));
    }

}
