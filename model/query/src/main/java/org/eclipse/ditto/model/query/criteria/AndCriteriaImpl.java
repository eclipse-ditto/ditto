/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.query.criteria;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Objects;

import org.eclipse.ditto.model.query.criteria.visitors.CriteriaVisitor;

/**
 * Criteria which performs a logical AND of arbitrary criterias.
 */
public class AndCriteriaImpl implements Criteria {

    private final List<Criteria> criterias;

    public AndCriteriaImpl(final List<Criteria> criterias) {
        this.criterias = requireNonNull(criterias);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitAnd(
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
        final AndCriteriaImpl that = (AndCriteriaImpl) o;
        return Objects.equals(criterias, that.criterias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), criterias);
    }
}
