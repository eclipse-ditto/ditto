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

import javax.annotation.Nullable;

/**
 * A projected {@code SELECT} column: an expression with an optional {@code AS <alias>}. The alias is a validated
 * identifier.
 */
final class SelectColumn implements SqlNode {

    private final SqlExpression expression;
    @Nullable
    private final String alias;

    SelectColumn(final SqlExpression expression, @Nullable final String alias) {
        this.expression = expression;
        this.alias = alias == null ? null : Identifiers.require(alias, "column alias");
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(expression);
        if (alias != null) {
            ctx.sql(" AS ").sql(alias);
        }
    }

}
