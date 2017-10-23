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
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;
import org.junit.Test;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.EqPredicateImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.FieldCriteriaImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.AttributeExpressionImpl;

/**
 * Tests class {@link FieldCriteriaImpl}.
 */
public class FieldCriteriaImplTest extends AbstractCriteriaTestBase {

    private static final String KNOWN_ATTR_KEY = "attributeKey";
    private static final String KNOWN_ATTR_VALUE = "attributeValue";

    /** */
    @Test
    public void fieldCriteriaValid() {
        final Bson expectedBson = Filters.elemMatch(PersistenceConstants.FIELD_INTERNAL, Filters
                .and(Filters.eq(
                        PersistenceConstants.FIELD_INTERNAL_KEY,
                        PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KNOWN_ATTR_KEY),
                        Filters.eq(PersistenceConstants.FIELD_INTERNAL_VALUE, KNOWN_ATTR_VALUE)));

        final Criteria actualCriteria =
                new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY),
                        new EqPredicateImpl(KNOWN_ATTR_VALUE));
        assertCriteria(expectedBson, actualCriteria);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullExpression() {
        new FieldCriteriaImpl(null, new EqPredicateImpl(KNOWN_ATTR_VALUE));
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void fieldCriteriaWithNullPredicate() {
        new FieldCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY), null);
    }

}
