/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

/**
 * Field expression for a specific desired property with a given feature id.
 *
 * @since 2.0.0
 */
public interface FeatureIdDesiredPropertyExpression extends SortFieldExpression, FilterFieldExpression,
        ExistsFieldExpression {

    /**
     * Creates an expression for a desired property belonging to a feature.
     *
     * @param featureId the feature id.
     * @param desiredProperty the feature desired property path.
     * @return the created FeatureIdDesiredPropertyExpression
     * @throws NullPointerException if any argument is {@code null}.
     */
    static FeatureIdDesiredPropertyExpression of(final String featureId, final String desiredProperty) {
        return new FeatureIdDesiredPropertyExpressionImpl(featureId, desiredProperty);
    }

    /**
     * @return the feature id.
     */
    String getFeatureId();

    /**
     * @return the desiredProperty path.
     */
    String getDesiredProperty();

}
