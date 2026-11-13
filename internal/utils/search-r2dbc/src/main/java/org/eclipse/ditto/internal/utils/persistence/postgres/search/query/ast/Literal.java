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
 * A fixed SQL keyword literal from the AST's own grammar — {@code TRUE}, {@code FALSE} or {@code NULL}. These are the
 * only value-shaped tokens rendered inline, and they carry NO caller data (e.g. the {@code false} fallback of the
 * {@code COALESCE(... , false)} auth rechecks in §3.3). Actual data values are always {@link Param} binds.
 */
enum Literal implements SqlExpression {

    TRUE("true"),
    FALSE("false"),
    NULL("NULL");

    private final String sql;

    Literal(final String sql) {
        this.sql = sql;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(sql);
    }

}
