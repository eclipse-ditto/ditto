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

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Abstract base class for multi-valued MongoDB predicate implementations.
 */
abstract class AbstractMultiPredicate implements Predicate {

    private final List<Object> values;

    /**
     * Create a new predicate with the given values.
     *
     * @param values the values
     */
    AbstractMultiPredicate(final List<?> values) {
        this.values = requireNonNull(Collections.unmodifiableList(new ArrayList<>(values)));
    }

    /**
     * @return the values
     */
    public List<Object> getValues() {
        return values;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractMultiPredicate that = (AbstractMultiPredicate) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " [value=" + getValues() + "]";
    }

}
