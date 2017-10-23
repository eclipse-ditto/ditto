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

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.Test;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.AnyCriteriaImpl;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;

/**
 * Tests {@link AnyCriteriaImpl}.
 */
public class AnyCriteriaImplTest extends AbstractCriteriaTestBase {

    /** */
    @Test
    public void any() {
        final Bson expectedBson = new BsonDocument();

        final Criteria actualCriteria = AnyCriteriaImpl.getInstance();
        assertCriteria(expectedBson, actualCriteria);
    }
}
