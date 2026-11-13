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
 * One common-table-expression of a {@code WITH} clause: {@code name AS [MATERIALIZED] (subquery)}. The
 * {@code MATERIALIZED} form is the §3.5 "rescue" strategy — forcing the selective leg to materialize first so an
 * auth-heavy EXISTS-chain does not nested-loop from an unselective leg (bench-results doc §5).
 */
final class Cte implements SqlNode {

    private final String name;
    private final boolean materialized;
    private final Select subquery;

    Cte(final String name, final boolean materialized, final Select subquery) {
        this.name = Identifiers.require(name, "CTE name");
        this.materialized = materialized;
        this.subquery = subquery;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(name).sql(" AS ");
        if (materialized) {
            ctx.sql("MATERIALIZED ");
        }
        ctx.sql("(").render(subquery).sql(")");
    }

}
