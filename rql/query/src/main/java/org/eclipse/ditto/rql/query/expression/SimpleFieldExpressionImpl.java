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
package org.eclipse.ditto.rql.query.expression;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.rql.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.rql.query.expression.visitors.SortFieldExpressionVisitor;

/**
 * Immutable implementation of {@link SimpleFieldExpression}.
 */
@Immutable
final class SimpleFieldExpressionImpl implements SimpleFieldExpression {

    private final String fieldName;

    SimpleFieldExpressionImpl(final String fieldName) {
        this.fieldName = requireNonNull(fieldName);
    }

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitSimple(fieldName);
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitSimple(fieldName);
    }

    @Override
    public <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor) {
        return visitor.visitSimple(fieldName);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitSimple(fieldName);
    }

    @Override
    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SimpleFieldExpressionImpl that = (SimpleFieldExpressionImpl) o;
        return Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "fieldName=" + fieldName +
                "]";
    }

}
