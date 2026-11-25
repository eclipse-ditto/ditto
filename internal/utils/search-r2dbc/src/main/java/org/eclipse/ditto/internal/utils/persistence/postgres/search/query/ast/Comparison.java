/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

/**
 * A binary comparison {@code left <op> right} (e.g. {@code sf.val_num > $1}). The right side is typically a
 * {@link Param} bind, so the compared value never reaches SQL text.
 */
final class Comparison implements SqlExpression {

    private final SqlExpression left;
    private final ComparisonOperator operator;
    private final SqlExpression right;

    Comparison(final SqlExpression left, final ComparisonOperator operator, final SqlExpression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(left).sql(" ").sql(operator.sql()).sql(" ").render(right);
    }

}
