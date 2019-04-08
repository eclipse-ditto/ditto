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

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GLOBAL_READ;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_GRANTED;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_KEY;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_INTERNAL_VALUE;
import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_REVOKED;

import java.util.Arrays;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.EqPredicateImpl;
import org.eclipse.ditto.model.query.criteria.FieldCriteriaImpl;
import org.eclipse.ditto.model.query.expression.AttributeExpressionImpl;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Tests class {@link FieldCriteriaImpl}.
 */
public class FieldCriteriaImplTest extends AbstractCriteriaTestBase {

    private static final String KNOWN_ATTR_KEY = "attributeKey";
    private static final String KNOWN_ATTR_VALUE = "attributeValue";

    @Test
    public void fieldCriteriaValid() {
        final List<String> subjectIds = Arrays.asList("subject:alpha", "subject:beta");

        final Bson expectedBson = Filters.and(
                // $elemMatch on actual attribute
                Filters.elemMatch(FIELD_INTERNAL, Filters.and(
                        Filters.eq(FIELD_INTERNAL_KEY, "/attributes/" + KNOWN_ATTR_KEY),
                        Filters.eq(FIELD_INTERNAL_VALUE, KNOWN_ATTR_VALUE),
                        Filters.and(
                                Filters.in(FIELD_GRANTED, subjectIds),
                                Filters.nin(FIELD_REVOKED, subjectIds)
                        ))),
                // $in on global-read to ensure visibility in the presence of negation
                Filters.in(FIELD_GLOBAL_READ, subjectIds));

        final Criteria actualCriteria =
                new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY),
                        new EqPredicateImpl(KNOWN_ATTR_VALUE));
        assertCriteria(expectedBson, actualCriteria, subjectIds);
    }


    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullExpression() {
        new FieldCriteriaImpl(null, new EqPredicateImpl(KNOWN_ATTR_VALUE));
    }


    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullPredicate() {
        new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY), null);
    }

}
