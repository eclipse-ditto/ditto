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
package org.eclipse.ditto.services.thingsearch.querymodel.query;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.SortFieldExpression;


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
