/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
 * Immutable implementation of {@link FeatureDefinitionExpression}.
 */
@Immutable
final class FeatureDefinitionExpressionImpl implements FeatureDefinitionExpression {

    private final String featureId;

    FeatureDefinitionExpressionImpl(final String featureId) {
        this.featureId = requireNonNull(featureId);
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDefinition(featureId);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDefinition(featureId);
    }

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDefinition(featureId);
    }

    @Override
    public <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDefinition(featureId);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeatureDefinitionExpressionImpl that = (FeatureDefinitionExpressionImpl) o;
        return Objects.equals(featureId, that.featureId);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(featureId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "featureId=" + featureId +
                "]";
    }

}
