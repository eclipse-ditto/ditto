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
package org.eclipse.ditto.model.query.expression.visitors;

import org.eclipse.ditto.model.query.expression.ExistsFieldExpression;

/**
 * Compositional interpreter of {@link ExistsFieldExpression}.
 */
public interface ExistsFieldExpressionVisitor<T>
        extends PolicyRestrictedFieldExpressionVisitor<T>, SortFieldExpressionVisitor<T> {

    @Override
    T visitAttribute(final String key);

    @Override
    T visitFeature(final String featureId);

    @Override
    T visitFeatureIdProperty(final String featureId, final String property);

    @Override
    T visitFeatureProperty(final String property);

    @Override
    T visitSimple(final String fieldName);
}
