/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.query.model.criteria;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.FIELD_ATTRIBUTES_PATH;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.ExistsCriteriaImpl;
import org.eclipse.ditto.model.query.expression.AttributeExpressionImpl;
import org.junit.Test;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants;

/**
 * Unit test for {@link ExistsCriteriaImpl}.
 */
public final class ExistsCriteriaImplTest extends AbstractCriteriaTestBase {

    private static final String STARTS_WITH = "^";
    private static final String ENDS_WITH = "(/|\\z)";
    private static final String KNOWN_ATTR_KEY = "attributeKey";

    @Test
    public void existsCriteriaValid() {
        final Bson expectedBson = Filters.regex(PersistenceConstants.FIELD_PATH_KEY,
                STARTS_WITH + FIELD_ATTRIBUTES_PATH + KNOWN_ATTR_KEY + ENDS_WITH);

        final Criteria actualCriteria = new ExistsCriteriaImpl(new AttributeExpressionImpl(KNOWN_ATTR_KEY));

        assertSudoCriteria(expectedBson, actualCriteria);
    }

    @Test(expected = NullPointerException.class)
    public void existsCriteriaWithNullExpression() {
        new ExistsCriteriaImpl(null);
    }

}
