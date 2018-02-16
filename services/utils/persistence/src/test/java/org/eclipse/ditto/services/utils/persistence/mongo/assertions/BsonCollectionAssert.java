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

    private void assertBothOrNonNull(final Object expected) {
        if (null == expected) {
            Assertions.assertThat(actual).isNull();
        } else {
            Assertions.assertThat(actual).isNotNull();
        }
    }

}
