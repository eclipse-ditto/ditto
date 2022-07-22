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
package org.eclipse.ditto.rql.query.expression.visitors;

import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;

/**
 * Compositional interpreter of {@link ExistsFieldExpression}.
 */
public interface ExistsFieldExpressionVisitor<T> extends SortFieldExpressionVisitor<T> {

    @Override
    T visitAttribute(String key);

    T visitFeature(String featureId);

    T visitFeatureDefinition(String featureId);

    T visitFeatureProperties(CharSequence featureId);

    T visitFeatureDesiredProperties(CharSequence featureId);

    @Override
    T visitFeatureIdProperty(String featureId, String property);

    @Override
    T visitFeatureIdDesiredProperty(CharSequence featureId, CharSequence property);

    @Override
    T visitSimple(String fieldName);

    T visitMetadata(String key);

}
