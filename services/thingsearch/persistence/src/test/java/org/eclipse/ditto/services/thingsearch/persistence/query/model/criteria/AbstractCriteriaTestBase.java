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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.EqPredicateImpl;
import org.eclipse.ditto.model.query.criteria.FieldCriteriaImpl;
import org.eclipse.ditto.model.query.expression.AttributeExpressionImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;

import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;


/**
 * Abstract base class for criteria-related tests.
 */
abstract class AbstractCriteriaTestBase {

    protected static final String KNOWN_ATTR_KEY = "attributeKey";

    /**
     * Known criteria 1 (for nested criteria test).
     */
    protected static final Criteria KNOWN_CRITERIA_1 =
            new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY), new EqPredicateImpl("attributeValue"));

    /**
     * Known criteria 2 (for nested criteria test).
     */
    protected static final Criteria KNOWN_CRITERIA_2 =
            new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY), new EqPredicateImpl("attributeValue2"));

    protected static void assertSudoCriteria(final Bson expectedBson, final Criteria actualCriteria) {
        requireNonNull(expectedBson);
        requireNonNull(actualCriteria);

        final Bson actualBson = CreateBsonVisitor.sudoApply(actualCriteria);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }

    protected static void assertCriteria(final Bson expectedBson, final Criteria actualCriteria,
            final List<String> authSubjects) {

        requireNonNull(expectedBson);
        requireNonNull(actualCriteria);
        requireNonNull(authSubjects);

        final Bson actualBson = CreateBsonVisitor.apply(actualCriteria, authSubjects);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }
}
