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

/**
 * A SQL function call {@code name(arg1, arg2, …)}. The function name is a fixed identifier from the AST grammar; the
 * arguments are arbitrary expressions.
 * <p>
 * This is the primitive for the wildcard-feature auth chain (§3.3): {@code jsonb_extract_path(st.features_auth, sf.f_id,
 * $k1, …)} threads the DYNAMIC {@code sf.f_id} column into a jsonb path alongside bound property-path key segments —
 * inexpressible with a static {@code #> '{…}'} array, so a function call is required.
 */
final class FunctionCall implements SqlExpression {

    private final String name;
    private final List<SqlExpression> arguments;

    FunctionCall(final String name, final List<SqlExpression> arguments) {
        this.name = Identifiers.require(name, "function name");
        this.arguments = List.copyOf(arguments);
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(name).sql("(").renderSeparated(arguments, ", ").sql(")");
    }

}
