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

import io.r2dbc.spi.Statement;

/**
 * The immutable output of rendering a {@link SqlNode}: the parameterized SQL text and the ordered list of bind values.
 * The bind at 0-based list index {@code i} is the {@code $(i + 1)} placeholder in {@link #sql()}.
 *
 * @param sql the SQL text, containing only {@code $n} placeholders for values (no inlined caller data).
 * @param binds the ordered binds, one per {@code $n} in ascending order.
 */
public record RenderedSql(String sql, List<SqlBind> binds) {

    public RenderedSql {
        binds = List.copyOf(binds);
    }

    /**
     * Binds every collected value onto an r2dbc {@link Statement} at its {@code $n} position ({@code $1} = index 0),
     * using the recorded type hints (typed {@code bindNull} for {@code null} values).
     *
     * @param statement the prepared statement created from {@link #sql()}.
     * @param <S> the statement type, returned for chaining.
     * @return the same statement.
     */
    public <S extends Statement> S applyTo(final S statement) {
        for (int i = 0; i < binds.size(); i++) {
            final SqlBind bind = binds.get(i);
            bind.type().bind(statement, i, bind.value());
        }
        return statement;
    }

    /**
     * @return just the ordered bind values (position = {@code $n} - 1), for test assertions / logging.
     */
    public List<Object> bindValues() {
        return binds.stream().map(SqlBind::value).toList();
    }

}
