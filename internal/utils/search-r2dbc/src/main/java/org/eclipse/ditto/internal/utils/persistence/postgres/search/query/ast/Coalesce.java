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
 * {@code COALESCE(a, b, …)}. Used by the auth rechecks ({@code COALESCE((policy_auth #> $p) ?| $s, false)}, §3.3) and by
 * the sort-key null coalescing ({@code COALESCE(k0.type_rank, 1)}, §3.5 sort parity requirement (a)).
 */
final class Coalesce implements SqlExpression {

    private final List<SqlExpression> arguments;

    Coalesce(final List<SqlExpression> arguments) {
        if (arguments == null || arguments.size() < 2) {
            throw new IllegalArgumentException("COALESCE requires at least two arguments");
        }
        this.arguments = List.copyOf(arguments);
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("COALESCE(").renderSeparated(arguments, ", ").sql(")");
    }

}
