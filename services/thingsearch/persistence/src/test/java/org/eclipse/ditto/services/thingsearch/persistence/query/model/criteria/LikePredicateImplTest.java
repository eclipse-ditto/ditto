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
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.criteria.LikePredicateImpl;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

import com.mongodb.client.model.Filters;

/**
 * Unit test for {@link LikePredicateImpl}.
 */
public final class LikePredicateImplTest {

    private static final String KNOWN_FIELD_NAME = "knownFieldName";
    private static final String KNOWN_VALUE = "knownValue*";
    private static final String ESCAPED_KNOWN_VALUE = "^\\QknownValue\\E.*\\Q\\E$";

    /** */
    @Test(expected = NullPointerException.class)
    public void withNullValue() {
        valueCheck(null);
    }

    /** */
    @Test
    public void startsWith() {
        valueCheck(KNOWN_VALUE);
    }

    private static void valueCheck(final String value) {
        final Bson expectedBson = Filters.regex(KNOWN_FIELD_NAME, ESCAPED_KNOWN_VALUE, "");
        final Bson actualBson = CreateBsonPredicateVisitor.apply(new LikePredicateImpl(value), KNOWN_FIELD_NAME);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }

}
