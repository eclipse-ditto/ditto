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
package org.eclipse.ditto.model.query.expression;

import org.eclipse.ditto.model.query.expression.visitors.ExistsFieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.FilterFieldExpressionVisitor;
import org.eclipse.ditto.model.query.expression.visitors.SortFieldExpressionVisitor;


/**
 * Sort field expression: Used to create query sorting options based on certain fields.
 */
public interface SortFieldExpression extends FilterFieldExpression, ExistsFieldExpression {

    /**
     * Evaluates this expression by a visitor.
     *
     * @param visitor The visitor to perform the evaluation.
     * @param <T> The result type.
     * @return The result of the evaluation.
     */
    <T> T acceptSortVisitor(final SortFieldExpressionVisitor<T> visitor);

    @Override
    <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor);

    @Override
    <T> T acceptExistsVisitor(final ExistsFieldExpressionVisitor<T> visitor);

    @Override
    <T> T accept(final FieldExpressionVisitor<T> visitor);
}
