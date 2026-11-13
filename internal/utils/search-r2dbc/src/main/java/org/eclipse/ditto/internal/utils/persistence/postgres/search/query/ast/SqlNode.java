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
 * A node of the parameterized SQL AST. Every node knows how to render <em>itself</em> into a {@link SqlRenderContext}
 * (fixed SQL text via {@link SqlRenderContext#sql}, values via {@link SqlRenderContext#bind}); the tree renders
 * depth-first, left-to-right, which is what makes the {@code $n} numbering deterministic.
 * <p>
 * The AST is the RQL-visitor target for Phases D2/D3: the visitors build a tree, and {@link #render(SqlNode)} turns it
 * into {@link RenderedSql}. It is a package-internal API of the search-r2dbc module.
 */
public interface SqlNode {

    /**
     * Renders this node into the given context, appending its SQL text and binds.
     *
     * @param ctx the shared render accumulator.
     */
    void render(SqlRenderContext ctx);

    /**
     * Renders a node from scratch into a fresh context.
     *
     * @param node the root node.
     * @return the rendered SQL + ordered binds.
     */
    static RenderedSql render(final SqlNode node) {
        return new SqlRenderContext().render(node).toRenderedSql();
    }

}
