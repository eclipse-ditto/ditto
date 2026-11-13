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
 * A {@code LEFT JOIN LATERAL (subquery) alias ON true} block — the §3.5 sort strategy: one lateral per sort key selects
 * the boundary sort element row-wise ({@code ORDER BY … LIMIT 1}), and its columns feed the outer {@code ORDER BY}. The
 * {@code LEFT}/{@code ON true} keeps rows whose key is missing (they sort as the null rank via the outer coalescing).
 */
final class LateralJoin implements SqlNode {

    private final Select subquery;
    private final String alias;

    LateralJoin(final Select subquery, final String alias) {
        this.subquery = subquery;
        this.alias = Identifiers.require(alias, "lateral alias");
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("LEFT JOIN LATERAL (").render(subquery).sql(") ").sql(alias).sql(" ON true");
    }

}
