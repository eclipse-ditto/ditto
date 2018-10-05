/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
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
