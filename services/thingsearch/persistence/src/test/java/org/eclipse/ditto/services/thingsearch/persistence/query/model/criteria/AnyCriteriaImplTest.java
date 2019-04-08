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

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.AnyCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.junit.Test;
/**
 * Tests {@link AnyCriteriaImpl}.
 */
public class AnyCriteriaImplTest extends AbstractCriteriaTestBase {


    @Test
    public void any() {
        final Bson expectedBson = new BsonDocument();

        final Criteria actualCriteria = AnyCriteriaImpl.getInstance();
        assertSudoCriteria(expectedBson, actualCriteria);
    }
}
