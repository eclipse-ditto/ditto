/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;

/**
 * Criteria implementation which can handle arbitrary field emptiness filters.
 * A field is considered "empty" when it is absent, {@code null}, an empty array, an empty object or an empty string.
 *
 * @since 3.9.0
 */
final class EmptyCriteriaImpl implements Criteria {

    private final ExistsFieldExpression fieldExpression;

    EmptyCriteriaImpl(final ExistsFieldExpression fieldExpression) {
        this.fieldExpression = requireNonNull(fieldExpression);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EmptyCriteriaImpl that = (EmptyCriteriaImpl) o;
        return Objects.equals(fieldExpression, that.fieldExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldExpression);
    }

    @Override
    public <T> T accept(final CriteriaVisitor<T> visitor) {
        return visitor.visitEmpty(fieldExpression);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [fieldExpression=" + fieldExpression + "]";
    }

}
