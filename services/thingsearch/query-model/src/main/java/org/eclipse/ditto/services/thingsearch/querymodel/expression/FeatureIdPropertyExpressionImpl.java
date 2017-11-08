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
package org.eclipse.ditto.services.thingsearch.querymodel.expression;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.SortFieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.PolicyRestrictedFieldExpressionVisitor;

/**
 * Field expression for feature properties with a given feature id.
 */
public class FeatureIdPropertyExpressionImpl implements SortFieldExpression, FilterFieldExpression,
        ExistsFieldExpression, PolicyRestrictedFieldExpression {

    private final String property;
    private final String featureId;

    /**
     * Constructor.
     *
     * @param featureId the feature id
     * @param property the feature property path
     */
    public FeatureIdPropertyExpressionImpl(final String featureId, final String property) {
        this.property = requireNonNull(property);
        this.featureId = requireNonNull(featureId);
    }

    @Override
    public <T> T acceptPolicyRestrictedVisitor(final PolicyRestrictedFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdProperty(featureId, property);
    }

    @Override
    public <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdProperty(featureId, property);
    }

    @Override
    public <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdProperty(featureId, property);
    }

    @Override
    public <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdProperty(featureId, property);
    }

    @Override
    public <T> T accept(final FieldExpressionVisitor<T> visitor) {
        return visitor.visitFeatureIdProperty(featureId, property);
    }

    /**
     * @return the feature id.
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * @return the property path.
     */
    public String getProperty() {
        return property;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if ((o == null) || (getClass() != o.getClass())) {
            return false;
        }
        final FeatureIdPropertyExpressionImpl that = (FeatureIdPropertyExpressionImpl) o;
        return Objects.equals(property, that.property) && Objects.equals(featureId, that.featureId);
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int hashCode() {
        return Objects.hash(property, featureId);
    }

    @Override
    public String toString() {
        return "FeatureIdPropertyExpression [featureId=" + featureId + ", property=" + property + "]";
    }
}
