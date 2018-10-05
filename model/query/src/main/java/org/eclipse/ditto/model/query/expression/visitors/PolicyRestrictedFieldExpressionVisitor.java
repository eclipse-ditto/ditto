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

import org.eclipse.ditto.model.query.expression.PolicyRestrictedFieldExpression;

/**
 * Compositional interpreter of
 * {@link PolicyRestrictedFieldExpression}.
 */
public interface PolicyRestrictedFieldExpressionVisitor<T> {

    T visitAttribute(final String key);

    T visitFeature(final String featureId);

    T visitFeatureIdProperty(final String featureId, final String property);

    T visitFeatureProperty(final String property);
}
