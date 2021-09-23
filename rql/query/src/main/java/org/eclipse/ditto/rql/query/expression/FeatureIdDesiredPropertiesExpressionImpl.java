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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.rql.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.rql.query.expression.visitors.FieldExpressionVisitor;

/**
 * Immutable implementation of {@link FeatureIdDesiredPropertiesExpression}.
 */
@Immutable
final class FeatureIdDesiredPropertiesExpressionImpl implements FeatureIdDesiredPropertiesExpression {

    private final String featureId;

    FeatureIdDesiredPropertiesExpressionImpl(final String featureId) {
        this.featureId = ConditionChecker.checkNotNull(featureId, "featureId");
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDesiredProperties(featureId);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureDesiredProperties(featureId);
    }

    @Override
    public String getFeatureId() {
        return featureId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeatureIdDesiredPropertiesExpressionImpl that = (FeatureIdDesiredPropertiesExpressionImpl) o;
        return featureId.equals(that.featureId);
    }

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
