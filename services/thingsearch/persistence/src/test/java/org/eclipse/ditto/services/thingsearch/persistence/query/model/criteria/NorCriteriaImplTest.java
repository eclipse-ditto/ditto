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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.junit.Test;

import com.mongodb.client.model.Filters;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.NorCriteriaImpl;

/**
 * Tests {@link NorCriteriaImpl}.
 */
public final class NorCriteriaImplTest extends AbstractCriteriaTestBase {

    /** */
    @Test(expected = NullPointerException.class)
    public void norWithNullSubCriteria() {
        new NorCriteriaImpl(null);
    }

    /** */
    @Test
    public void norWithEmptySubCriteria() {
        nor(Collections.emptyList());
    }

    private static void nor(final List<Criteria> orCriteria) {
        final Iterable<Bson> bsonObjects =
                orCriteria.stream().map(CreateBsonVisitor::apply).collect(Collectors.toList());
        final Bson expectedBson = Filters.nor(bsonObjects);

        final Criteria actualCriteria = new NorCriteriaImpl(orCriteria);
        assertCriteria(expectedBson, actualCriteria);
    }

    /** */
    @Test
    public void norWithOneSubCriteria() {
        nor(Collections.singletonList(KNOWN_CRITERIA_1));
    }

    /** */
    @Test
    public void norWithMoreThanOneSubCriteria() {
        nor(Arrays.asList(KNOWN_CRITERIA_1, KNOWN_CRITERIA_2));
    }

}
