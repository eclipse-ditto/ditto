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
package org.eclipse.ditto.rql.query.criteria;


import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Abstract base class for single-valued MongoDB predicate implementations.
 */
abstract class AbstractSinglePredicate implements Predicate {

    @Nullable private final Object value;

    /**
     * Create a new predicate with the given value.
     *
     * @param value the value, may be {@code null}
     */
    AbstractSinglePredicate(@Nullable final Object value) {
        this.value = value;
    }


    /**
     * Returns the value this Predicate encapsulated.
     *
     * @return the value this Predicate encapsulated.
     */
    @Nullable
    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractSinglePredicate that = (AbstractSinglePredicate) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " [value=" + getValue() + "]";
    }
}
