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

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.AnyCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.junit.Test;
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
