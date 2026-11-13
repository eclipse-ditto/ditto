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
 * One {@code ORDER BY} key: an expression with an ascending/descending direction. Null-ordering parity (missing == null,
 * §3.5 sort requirement (a)) is handled by wrapping the key in a {@link Coalesce}/{@link Case}, not by {@code NULLS
 * FIRST/LAST}, so this node only carries the direction.
 */
final class OrderByItem implements SqlNode {

    private final SqlExpression expression;
    private final boolean descending;

    OrderByItem(final SqlExpression expression, final boolean descending) {
        this.expression = expression;
        this.descending = descending;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(expression);
        if (descending) {
            ctx.sql(" DESC");
        }
    }

}
