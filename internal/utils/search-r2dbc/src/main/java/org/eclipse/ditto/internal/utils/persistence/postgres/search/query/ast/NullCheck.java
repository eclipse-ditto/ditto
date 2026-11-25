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
 * An {@code <expr> IS NULL} / {@code <expr> IS NOT NULL} test.
 */
final class NullCheck implements SqlExpression {

    private final SqlExpression operand;
    private final boolean negated;

    NullCheck(final SqlExpression operand, final boolean negated) {
        this.operand = operand;
        this.negated = negated;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(operand).sql(negated ? " IS NOT NULL" : " IS NULL");
    }

}
