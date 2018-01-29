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
package org.eclipse.ditto.services.utils.persistence.mongo.assertions;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil;

/**
 * Assertion methods for {@link Bson}.
 */
public final class BsonAssert extends AbstractAssert<BsonAssert, Bson> {

    /**
     * Constructs a {@code BsonAssert} object.
     *
     * @param value the BSON to be verified.
     */
    public BsonAssert(final Bson value) {
        super(value, BsonAssert.class);
    }

    /**
     * Asserts that the specified Bson is equal to this value.
     *
     * @param expected the expected Bson.
     * @return this assertion object to allow method chaining.
     */
    public BsonAssert isEqualTo(final Bson expected) {
        assertBothOrNonNull(expected);

        final BsonDocument expectedDoc = BsonUtil.toBsonDocument(expected);
        final BsonDocument actualDoc = BsonUtil.toBsonDocument(actual);
        Assertions.assertThat(actualDoc).isEqualTo(expectedDoc);

        return myself;
    }

    private void assertBothOrNonNull(final Object expected) {
        if (null == expected) {
            Assertions.assertThat(actual).isNull();
        } else {
            Assertions.assertThat(actual).isNotNull();
        }
    }

}
