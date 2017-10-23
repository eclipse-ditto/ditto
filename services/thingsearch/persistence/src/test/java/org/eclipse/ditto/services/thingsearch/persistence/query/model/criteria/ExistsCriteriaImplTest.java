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
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.ExistsCriteriaImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.AttributeExpressionImpl;

/**
 * Unit test for {@link ExistsCriteriaImpl}.
 */
public final class ExistsCriteriaImplTest extends AbstractCriteriaTestBase {

    private static final String STARTS_WITH = "^";
    private static final String ENDS_WITH = "(/|\\z)";
    private static final String KNOWN_ATTR_KEY = "attributeKey";

    /** */
    @Test
    public void existsCriteriaValid() {
        final Bson expectedBson = Filters.regex(PersistenceConstants.FIELD_PATH_KEY,
                STARTS_WITH + PersistenceConstants.FIELD_ATTRIBUTE_PREFIX_WITH_ENDING_SLASH + KNOWN_ATTR_KEY +
                        ENDS_WITH);

        final Criteria actualCriteria = new ExistsCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY));

        assertCriteria(expectedBson, actualCriteria);
    }

    /** */
    @Test(expected = NullPointerException.class)
    public void existsCriteriaWithNullExpression() {
        new ExistsCriteriaImpl(null);
    }

}
