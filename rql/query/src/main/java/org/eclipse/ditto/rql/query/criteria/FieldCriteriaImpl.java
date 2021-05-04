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

import java.util.Objects;

import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * Criteria implementation which can handle arbitrary field expressions and predicates.
 */
final class FieldCriteriaImpl implements Criteria {

    private final FilterFieldExpression fieldExpression;
    private final Predicate predicate;

    public FieldCriteriaImpl(final FilterFieldExpression fieldExpression, final Predicate predicate) {
        this.fieldExpression = requireNonNull(fieldExpression);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FieldCriteriaImpl that = (FieldCriteriaImpl) o;
        return Objects.equals(fieldExpression, that.fieldExpression) &&
                Objects.equals(predicate, that.predicate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fieldExpression, predicate);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitField(fieldExpression, predicate);
    }
}
