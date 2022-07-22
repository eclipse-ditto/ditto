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

/**
 * Compositional interpreter of {@link SortFieldExpressionVisitor}.
 */
public interface SortFieldExpressionVisitor<T> {

    T visitAttribute(String key);

    T visitFeatureIdProperty(String featureId, String property);

    T visitFeatureDefinition(String featureId);

    T visitFeatureIdDesiredProperty(CharSequence featureId, CharSequence desiredProperty);

    T visitSimple(String fieldName);
}
