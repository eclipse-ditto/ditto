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

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;


/**
 * Criteria which performs a logical OR of arbitrary criterias.
 */
public class OrCriteriaImpl implements Criteria {

    private final List<Criteria> criterias;

    public OrCriteriaImpl(final List<Criteria> criterias) {
        this.criterias = requireNonNull(criterias);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitOr(
                criterias.stream().map(child -> child.accept(visitor)));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OrCriteriaImpl that = (OrCriteriaImpl) o;
        return Objects.equals(criterias, that.criterias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), criterias);
    }
}
