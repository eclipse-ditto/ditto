/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 * Immutable implementation of {@link FeatureIdDesiredPropertyExpression}.
 */
@Immutable
final class FeatureIdDesiredPropertyExpressionImpl implements FeatureIdDesiredPropertyExpression {

    private final String desiredProperty;
    private final String featureId;

    FeatureIdDesiredPropertyExpressionImpl(final String featureId, final String desiredProperty) {
        this.desiredProperty = requireNonNull(desiredProperty);
        this.featureId = requireNonNull(featureId);
    }

    @Override
    public <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdDesiredProperty(featureId, desiredProperty);
    }

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdDesiredProperty(featureId, desiredProperty);
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdDesiredProperty(featureId, desiredProperty);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdDesiredProperty(featureId, desiredProperty);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public String getDesiredProperty() {
        return desiredProperty;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeatureIdDesiredPropertyExpressionImpl that = (FeatureIdDesiredPropertyExpressionImpl) o;
        return Objects.equals(desiredProperty, that.desiredProperty) &&
                Objects.equals(featureId, that.featureId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(desiredProperty, featureId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "desiredProperty=" + desiredProperty +
                ", featureId=" + featureId +
                "]";
    }

}
