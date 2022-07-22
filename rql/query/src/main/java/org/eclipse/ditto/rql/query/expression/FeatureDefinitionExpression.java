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

/**
 * Field expression for feature definition with a given feature id.
 *
 * @since 3.0.0
 */
public interface FeatureDefinitionExpression extends SortFieldExpression, FilterFieldExpression, ExistsFieldExpression {

    /**
     * Creates an expression for existence of a feature definition.
     *
     * @param featureId the feature id.
     * @return the created FeatureExpression
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    static FeatureDefinitionExpression of(final String featureId) {
        return new FeatureDefinitionExpressionImpl(featureId);
    }

    /**
     * @return the feature id.
     */
    String getFeatureId();

}
