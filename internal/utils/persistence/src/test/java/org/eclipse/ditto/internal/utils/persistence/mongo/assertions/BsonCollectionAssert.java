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
import java.util.Comparator;

import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.Assertions;
import org.bson.conversions.Bson;

/**
 * Assertion methods for a collection of {@link Bson} objects.
 */
public final class BsonCollectionAssert
        extends AbstractIterableAssert<BsonCollectionAssert, Collection<Bson>, Bson, BsonAssert> {

    private final Comparator<Bson> areBsonObjectsEqualComparator;

    /**
     * Constructs a new {@code BsonCollectionAssert} object.
     *
     * @param bsonCollection the collection of {@link Bson} objects to be verified.
     */
    public BsonCollectionAssert(final Collection<Bson> bsonCollection) {
        super(bsonCollection, BsonCollectionAssert.class);
        areBsonObjectsEqualComparator = (expected, actual) -> {
            BsonAssertions.assertThat(actual).isEqualTo(expected);
            return 0;
        };
    }

    /**
     * Asserts that the specified collection of Bson objects is equal to this value.
     * <em>The order of the collections is taken into account!</em>
     *
     * @param expected the expected collection of Bson objects.
     * @return this assertion object to allow method chaining.
     */
    public <T extends Bson> BsonCollectionAssert isEqualTo(final Collection<T> expected) {
        assertBothOrNonNull(expected);

        Assertions.assertThat(actual)
                .isEqualTo(expected)
                .usingElementComparator(areBsonObjectsEqualComparator);

        return myself;
    }

    /**
     * Asserts that the specified collection of Bson objects is equal to this value.
     *
     * @param expected the expected collection of Bson objects.
     * @return this assertion object to allow method chaining.
     */
    public <T extends Bson> BsonCollectionAssert isEqualToInAnyOrder(final Iterable<T> expected) {
        assertBothOrNonNull(expected);

        Assertions.assertThat(actual)
                .hasSameElementsAs(expected)
                .usingElementComparator(areBsonObjectsEqualComparator);

        return myself;
    }

    @Override
    protected BsonAssert toAssert(final Bson value, final String description) {
        return new BsonAssert(value).as(description);
    }

    @Override
    protected BsonCollectionAssert newAbstractIterableAssert(final Iterable<? extends Bson> iterable) {
        return new BsonCollectionAssert((Collection<Bson>) iterable);
    }

    private void assertBothOrNonNull(final Object expected) {
        if (null == expected) {
            Assertions.assertThat(actual).isNull();
        } else {
            Assertions.assertThat(actual).isNotNull();
        }
    }

}
