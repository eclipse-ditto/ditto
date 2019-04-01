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

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.InPredicateImpl;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Unit test for {@link InPredicateImpl}.
 */
public final class InPredicateImplTest {

    private static final String KNOWN_FIELD_NAME = "knownFieldName";
    private static final String[] KNOWN_VALUE = {"knownValue1", "knownValue2", "knownValue3"};

    /** */
    @Test(expected = NullPointerException.class)
    public void withNullValue() {
        new InPredicateImpl(null);
    }

    /** */
    @Test
    public void withStringValue() {
        final List<Object> values = new ArrayList<>();
        values.add(KNOWN_VALUE[0]);
        values.add(KNOWN_VALUE[1]);
        values.add(KNOWN_VALUE[2]);
        withValues(values);
    }

    private static void withValues(final List<Object> values) {
        final Bson expectedBson = Filters.in(KNOWN_FIELD_NAME, values);
        final Bson actualBson = CreateBsonPredicateVisitor.apply(new InPredicateImpl(values), KNOWN_FIELD_NAME);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }

}
