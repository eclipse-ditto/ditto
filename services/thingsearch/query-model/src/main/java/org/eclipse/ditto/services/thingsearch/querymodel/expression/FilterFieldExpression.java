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
package org.eclipse.ditto.services.thingsearch.querymodel.expression;

import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FieldExpressionVisitor;
import org.eclipse.ditto.services.thingsearch.querymodel.expression.visitors.FilterFieldExpressionVisitor;

/**
 * Filter field expression: Used to create a query filter based on certain fields.
 */
public interface FilterFieldExpression extends FieldExpression {

    /**
     * Evaluates this expression by a visitor.
     *
     * @param visitor The visitor to perform the evaluation.
     * @param <T> The result type.
     * @return The result of the evaluation.
     */
    <T> T acceptFilterVisitor(final FilterFieldExpressionVisitor<T> visitor);

    @Override
    <T> T accept(final FieldExpressionVisitor<T> visitor);
}
