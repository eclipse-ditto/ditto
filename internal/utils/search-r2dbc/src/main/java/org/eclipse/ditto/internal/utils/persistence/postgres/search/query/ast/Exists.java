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
 * An {@code EXISTS (subquery)} / {@code NOT EXISTS (subquery)} predicate — the core value-probe shape (§3.5): every RQL
 * predicate becomes an {@code EXISTS} over {@code search_flat} correlated to {@code st.thing_id} (and, for wildcard
 * features, to an outer flat alias for per-{@code f_id} scoping). {@code NOT EXISTS} preserves Mongo {@code nor}
 * semantics.
 */
final class Exists implements SqlExpression {

    private final Select subquery;
    private final boolean negated;

    Exists(final Select subquery, final boolean negated) {
        this.subquery = subquery;
        this.negated = negated;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(negated ? "NOT EXISTS (" : "EXISTS (").render(subquery).sql(")");
    }

}
