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
package org.eclipse.ditto.model.query.expression;

import java.util.Objects;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FieldExpressionVisitor;

/**
 * Field expression for feature desired properties.
 *
 * @since 1.5.0
 */
public final class FeatureIdDesiredPropertiesExpressionImpl implements ExistsFieldExpression {

    private final String featureId;

    /**
     * Constructor.
     *
     * @param featureId the feature ID.
     */
    public FeatureIdDesiredPropertiesExpressionImpl(final String featureId) {
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

    /**
     * @return the feature ID.
     */
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
        return "FeatureIdDesiredPropertiesExpression [featureId=" + featureId + "]";
    }

}