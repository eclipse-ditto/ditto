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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The mutable accumulator threaded through a single {@link SqlNode#render} traversal: it grows the SQL text and collects
 * the ordered bind list, assigning each bind its {@code $n} number in strict left-to-right append order. That ordering
 * is the whole correctness contract of the renderer — {@code $n} numbering is deterministic across arbitrary nesting
 * because every {@link #bind} call appends the next number as it is reached in the traversal.
 * <p>
 * A value ALWAYS goes through {@link #bind}; SQL text is only ever produced by {@link #sql} from the AST's own fixed
 * grammar (and the {@code assert}-guarded {@link RawFragment}). No caller-supplied value can reach the SQL string.
 */
public final class SqlRenderContext {

    private final StringBuilder sql = new StringBuilder();
    private final List<SqlBind> binds = new ArrayList<>();

    /**
     * Appends fixed SQL text (from the AST grammar — never a caller value).
     *
     * @param fixedSql the text to append.
     * @return this context.
     */
    public SqlRenderContext sql(final String fixedSql) {
        sql.append(fixedSql);
        return this;
    }

    /**
     * Appends the next {@code $n} placeholder (with the type's cast suffix, e.g. {@code $3::text[]}) and records the bind
     * value + type. This is the ONLY way a value enters a rendered statement.
     *
     * @param value the value to bind; may be {@code null} (bound as a typed SQL {@code NULL}).
     * @param type the r2dbc type hint.
     * @return this context.
     */
    public SqlRenderContext bind(@Nullable final Object value, final SqlBindType type) {
        binds.add(new SqlBind(value, type));
        sql.append('$').append(binds.size()).append(type.castSuffix());
        return this;
    }

    /**
     * Renders a child node into this same context (so its binds continue the running {@code $n} sequence).
     *
     * @param node the node to render.
     * @return this context.
     */
    public SqlRenderContext render(final SqlNode node) {
        node.render(this);
        return this;
    }

    /**
     * Renders a list of nodes separated by fixed text.
     *
     * @param nodes the nodes.
     * @param separator the fixed separator (e.g. {@code ", "} or {@code " AND "}).
     * @return this context.
     */
    public SqlRenderContext renderSeparated(final List<? extends SqlNode> nodes, final String separator) {
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                sql.append(separator);
            }
            nodes.get(i).render(this);
        }
        return this;
    }

    /**
     * @return the immutable rendering result (SQL text + ordered binds).
     */
    public RenderedSql toRenderedSql() {
        return new RenderedSql(sql.toString(), List.copyOf(binds));
    }

}
