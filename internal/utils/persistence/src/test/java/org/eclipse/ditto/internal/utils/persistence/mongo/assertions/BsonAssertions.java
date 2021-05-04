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
package org.eclipse.ditto.internal.utils.persistence.mongo.assertions;

import java.util.Collection;

import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;

/**
 * Entry point for assertion methods for different types. This is the same as {@link Assertions} with additional methods
 * for verifying {@link Bson} objects.
 */
public class BsonAssertions extends Assertions {

    /**
     * Creates an assertion for {@link Bson}.
     *
     * @param actual the actual value.
     * @return the created assertion object.
     */
    public static BsonAssert assertThat(final Bson actual) {
        return new BsonAssert(actual);
    }

    /**
     * Creates an assertion for a collection of {@link Bson}.
     * @param actual the actual value.
     * @return the created assertion object.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Bson> BsonCollectionAssert assertThat(final Collection<T> actual) {
        return new BsonCollectionAssert((Collection<Bson>) actual);
    }

}
