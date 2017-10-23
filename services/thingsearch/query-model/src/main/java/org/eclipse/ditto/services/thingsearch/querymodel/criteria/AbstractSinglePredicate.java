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
package org.eclipse.ditto.services.thingsearch.querymodel.criteria;


import java.util.Objects;

/**
 * Abstract base class for single-valued MongoDB predicate implementations.
 */
abstract class AbstractSinglePredicate implements Predicate {

    private final Object value;

    /**
     * Create a new predicate with the given value.
     *
     * @param value the value, may be {@code null}
     */
    AbstractSinglePredicate(final Object value) {
        this.value = value;
    }


    /**
     * Returns the value this Predicate encapsulated.
     *
     * @return the value this Predicate encapsulated.
     */
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
