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
package org.eclipse.ditto.rql.query;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.rql.query.expression.SortFieldExpression;


/**
 * SortOption is defined by a {@link SortFieldExpression} and a {@link SortDirection}.
 */
public final class SortOption {

    private final SortFieldExpression sortExpression;
    private final SortDirection sortDirection;

    /**
     * Constructor.
     *
     * @param sortExpression the FieldExpression
     * @param sortDirection the SortDirection
     */
    public SortOption(final SortFieldExpression sortExpression, final SortDirection sortDirection) {
        this.sortExpression = requireNonNull(sortExpression);
        this.sortDirection = requireNonNull(sortDirection);
    }

    /**
     * @return the sortExpression
     */
    public SortFieldExpression getSortExpression() {
        return sortExpression;
    }

    /**
     * @return the sortDirection
     */
    public SortDirection getSortDirection() {
        return sortDirection;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SortOption that = (SortOption) o;
        return Objects.equals(sortExpression, that.sortExpression) && Objects.equals(sortDirection, that.sortDirection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sortExpression, sortDirection);
    }

    @Override
    public String toString() {
        return "SortOption [sortExpression=" + sortExpression + ", sortDirection=" + sortDirection + "]";
    }

}
