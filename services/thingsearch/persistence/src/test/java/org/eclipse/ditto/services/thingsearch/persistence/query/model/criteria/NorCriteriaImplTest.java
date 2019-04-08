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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.model.query.criteria.NorCriteriaImpl;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Tests {@link NorCriteriaImpl}.
 */
public final class NorCriteriaImplTest extends AbstractCriteriaTestBase {


    @Test(expected = NullPointerException.class)
    public void norWithNullSubCriteria() {
        new NorCriteriaImpl(null);
    }


    @Test
    public void norWithEmptySubCriteria() {
        nor(Collections.emptyList());
    }

    private static void nor(final List<Criteria> orCriteria) {
        final Iterable<Bson> bsonObjects =
                orCriteria.stream().map(CreateBsonVisitor::sudoApply).collect(Collectors.toList());
        final Bson expectedBson = Filters.nor(bsonObjects);

        final Criteria actualCriteria = new NorCriteriaImpl(orCriteria);
        assertSudoCriteria(expectedBson, actualCriteria);
    }


    @Test
    public void norWithOneSubCriteria() {
        nor(Collections.singletonList(KNOWN_CRITERIA_1));
    }


    @Test
    public void norWithMoreThanOneSubCriteria() {
        nor(Arrays.asList(KNOWN_CRITERIA_1, KNOWN_CRITERIA_2));
    }

}
