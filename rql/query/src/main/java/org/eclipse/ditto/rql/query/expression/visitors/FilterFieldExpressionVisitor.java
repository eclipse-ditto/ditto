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

import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;

/**
 * Compositional interpreter of {@link FilterFieldExpression}.
 */
public interface FilterFieldExpressionVisitor<T> extends SortFieldExpressionVisitor<T> {

    @Override
    T visitAttribute(String key);

    @Override
    T visitFeatureIdProperty(String featureId, String property);

    @Override
    T visitFeatureIdDesiredProperty(CharSequence featureId, CharSequence desiredProperty);

    @Override
    T visitSimple(String fieldName);

    T visitMetadata(String key);

}
