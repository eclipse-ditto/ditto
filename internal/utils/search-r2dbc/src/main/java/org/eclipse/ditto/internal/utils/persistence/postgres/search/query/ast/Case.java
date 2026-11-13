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

import javax.annotation.Nullable;

/**
 * A searched {@code CASE WHEN … THEN … [ELSE …] END} expression. Needed by the sort layer's empty-array→null-rank
 * mapping (§3.2/§3.5 sort parity requirement (d)).
 */
final class Case implements SqlExpression {

    private final List<CaseBranch> branches;
    @Nullable
    private final SqlExpression elseResult;

    Case(final List<CaseBranch> branches, @Nullable final SqlExpression elseResult) {
        if (branches == null || branches.isEmpty()) {
            throw new IllegalArgumentException("CASE requires at least one WHEN branch");
        }
        this.branches = List.copyOf(branches);
        this.elseResult = elseResult;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("CASE");
        for (final CaseBranch branch : branches) {
            ctx.sql(" WHEN ").render(branch.condition()).sql(" THEN ").render(branch.result());
        }
        if (elseResult != null) {
            ctx.sql(" ELSE ").render(elseResult);
        }
        ctx.sql(" END");
    }

}
