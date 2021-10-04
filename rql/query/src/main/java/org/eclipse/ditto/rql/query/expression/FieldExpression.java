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
package org.eclipse.ditto.rql.query.expression;

import org.eclipse.ditto.rql.query.expression.visitors.FieldExpressionVisitor;

/**
 * Field expression: Used to create criteria based on certain fields.
 *
 * @see org.eclipse.ditto.rql.query.criteria.CriteriaFactory
 */
public interface FieldExpression {

    /**
     * Evaluates this expression by a visitor.
     *
     * @param visitor The visitor to perform the evaluation.
     * @param <T> The result type.
     * @return The result of the evaluation.
     */
    <T> T accept(final FieldExpressionVisitor<T> visitor);

}
