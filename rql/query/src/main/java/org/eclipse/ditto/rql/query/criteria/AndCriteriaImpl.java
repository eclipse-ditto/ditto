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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;

/**
 * Criteria which performs a logical AND of arbitrary criterias.
 */
final class AndCriteriaImpl implements Criteria {

    private final List<Criteria> criterias;

    public AndCriteriaImpl(final List<Criteria> criterias) {
        this.criterias = requireNonNull(criterias);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitAnd(
                criterias.stream().map(child -> child.accept(visitor)).collect(Collectors.toList()));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AndCriteriaImpl that = (AndCriteriaImpl) o;
        return Objects.equals(criterias, that.criterias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), criterias);
    }
}
