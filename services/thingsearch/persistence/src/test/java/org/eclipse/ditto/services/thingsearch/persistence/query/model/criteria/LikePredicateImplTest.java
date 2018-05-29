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

    private static final String KNOWN_VALUE_STARTS_WITH = "knownValue*";
    private static final String ESCAPED_KNOWN_VALUE_STARTS_WITH = "^\\QknownValue\\E";

    private static final String KNOWN_VALUE_ENDS_WITH = "*knownValue";
    private static final String ESCAPED_KNOWN_VALUE_ENDS_WITH = "\\QknownValue\\E$";

    private static final String KNOWN_VALUE_CONTAINS = "known*Value";
    private static final String ESCAPED_KNOWN_VALUE_CONTAINS = "^\\Qknown\\E.*\\QValue\\E$";

    private static final String KNOWN_VALUE_REPEATING = "**known***Value****";
    private static final String ESCAPED_KNOWN_VALUE_REPEATING = "\\Qknown\\E.*\\QValue\\E";

    private static final String KNOWN_VALUE_ONLY_WILDCARDS = "***";
    private static final String ESCAPED_KNOWN_VALUE_ONLY_WILDCARDS = ".*";

    private static final String KNOWN_VALUE_MINIMAL_PRE_AND_POSTFIX = "*a*";
    private static final String ESCAPED_KNOWN_VALUE_MINIMAL_PRE_AND_POSTFIX = "\\Qa\\E";

    /** */
    @Test(expected = NullPointerException.class)
    public void withNullValue() {
        valueCheck(null, ESCAPED_KNOWN_VALUE_STARTS_WITH);
    }

    /** */
    @Test
    public void startsWith() {
        valueCheck(KNOWN_VALUE_STARTS_WITH, ESCAPED_KNOWN_VALUE_STARTS_WITH);
    }

    /** */
    @Test
    public void contains() {
        valueCheck(KNOWN_VALUE_CONTAINS, ESCAPED_KNOWN_VALUE_CONTAINS);
    }

    /** */
    @Test
    public void endsWith() {
        valueCheck(KNOWN_VALUE_ENDS_WITH, ESCAPED_KNOWN_VALUE_ENDS_WITH);
    }

    /** */
    @Test
    public void repeatingWildcard() {
        valueCheck(KNOWN_VALUE_REPEATING, ESCAPED_KNOWN_VALUE_REPEATING);
    }

    /** */
    @Test
    public void onlyWildcards() {
        valueCheck(KNOWN_VALUE_ONLY_WILDCARDS, ESCAPED_KNOWN_VALUE_ONLY_WILDCARDS);
    }

    /** */
    @Test
    public void preAndPostfix() {
        valueCheck(KNOWN_VALUE_MINIMAL_PRE_AND_POSTFIX, ESCAPED_KNOWN_VALUE_MINIMAL_PRE_AND_POSTFIX);
    }

    private static void valueCheck(final String value, final String expected) {
        final Bson expectedBson = Filters.regex(KNOWN_FIELD_NAME, expected, "");
        final Bson actualBson = CreateBsonPredicateVisitor.apply(new LikePredicateImpl(value), KNOWN_FIELD_NAME);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }
}