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
 * Field expression for a specific property with a given feature id.
 *
 * @since 2.0.0
 */
public interface FeatureIdPropertyExpression extends SortFieldExpression, FilterFieldExpression, ExistsFieldExpression {

    /**
     * Creates an expression for a property belonging to a feature.
     *
     * @param featureId the feature id.
     * @param property the feature property path.
     * @return the created FeatureIdPropertyExpression
     * @throws NullPointerException if any argument is {@code null}.
     */
    static FeatureIdPropertyExpression of(final String featureId, final String property) {
        return new FeatureIdPropertyExpressionImpl(featureId, property);
    }

    /**
     * @return the feature id.
     */
    String getFeatureId();

    /**
     * @return the property path.
     */
    String getProperty();

}
