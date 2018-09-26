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

import javax.annotation.Nullable;

import org.bson.conversions.Bson;
import org.eclipse.ditto.model.query.criteria.Predicate;
import org.eclipse.ditto.services.thingsearch.persistence.read.criteria.visitors.CreateBsonPredicateVisitor;
import org.eclipse.ditto.services.utils.persistence.mongo.assertions.BsonAssertions;
import org.junit.Test;

/**
 * Common base implementation for predicate unit tests.
 */
abstract class AbstractPredicateTest {

    static final String KNOWN_FIELD_NAME = "knownFieldName";
    static final String KNOWN_VALUE = "knownValue";

    /** */
    @Test
    public void withNullValue() {
        withValue(null);
    }

    /** */
    @Test
    public void withStringValue() {
        withValue(KNOWN_VALUE);
    }

    protected void withValue(@Nullable final String value) {
        final Bson expectedBson = getFilter(value);
        final Bson actualBson = CreateBsonPredicateVisitor.apply(getPredicate(value), KNOWN_FIELD_NAME);

        BsonAssertions.assertThat(actualBson).isEqualTo(expectedBson);
    }

    protected abstract Bson getFilter(@Nullable String value);

    protected abstract Predicate getPredicate(@Nullable String value);

}
