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
package org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.PolicyRestrictedFieldExpression;

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
