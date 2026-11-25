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

import java.util.List;

/**
 * A boolean {@code OR} of two or more expressions, rendered fully parenthesized ({@code (a OR b)}). A single operand
 * renders as itself.
 */
final class Or implements SqlExpression {

    private final List<SqlExpression> operands;

    Or(final List<SqlExpression> operands) {
        if (operands == null || operands.isEmpty()) {
            throw new IllegalArgumentException("OR requires at least one operand");
        }
        this.operands = List.copyOf(operands);
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        if (operands.size() == 1) {
            operands.get(0).render(ctx);
        } else {
            ctx.sql("(").renderSeparated(operands, " OR ").sql(")");
        }
    }

}
