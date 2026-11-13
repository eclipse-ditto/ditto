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
 * Boolean negation, rendered {@code NOT <operand>}. {@code NOT} binds tighter than {@code AND}/{@code OR} but looser
 * than a comparison, so {@code NOT COALESCE(...)} and {@code NOT EXISTS (...)} render exactly as §3.3/§3.5 require; a
 * compound operand ({@link And}/{@link Or}) parenthesizes itself.
 */
final class Not implements SqlExpression {

    private final SqlExpression operand;

    Not(final SqlExpression operand) {
        if (operand == null) {
            throw new NullPointerException("NOT operand must not be null");
        }
        this.operand = operand;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("NOT ").render(operand);
    }

}
