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
import org.eclipse.ditto.model.query.criteria.AndCriteriaImpl;
import org.eclipse.ditto.model.query.criteria.Criteria;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.junit.Test;

import com.mongodb.client.model.Filters;
/**
 * Tests {@link AndCriteriaImpl}.
 */
public final class AndCriteriaImplTest extends AbstractCriteriaTestBase {

    /** */
    @Test(expected = NullPointerException.class)
    public void andWithNullSubCriteria() {
        new AndCriteriaImpl(null);
    }

    /** */
    @Test
    public void andWithEmptySubCriteria() {
        and(Collections.emptyList());
    }

    private static void and(final List<Criteria> andCriteria) {
        final Iterable<Bson> bsonObjects =
                andCriteria.stream().map(CreateBsonVisitor::apply).collect(Collectors.toList());
        final Bson expectedBson = Filters.and(bsonObjects);

        final Criteria actualCriteria = new AndCriteriaImpl(andCriteria);
        assertCriteria(expectedBson, actualCriteria);
    }

    /** */
    @Test
    public void andWithOneSubCriteria() {
        and(Collections.singletonList(KNOWN_CRITERIA_1));
    }

    /** */
    @Test
    public void andWithMoreThanOneSubCriteria() {
        and(Arrays.asList(KNOWN_CRITERIA_1, KNOWN_CRITERIA_2));
    }

}
