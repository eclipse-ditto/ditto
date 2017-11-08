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

import java.util.Objects;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.FilterFieldExpression;

/**
 * Criteria implementation which can handle arbitrary field expressions and predicates.
 */
public class FieldCriteriaImpl implements Criteria {

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
