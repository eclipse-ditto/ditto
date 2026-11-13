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
 * The jsonb {@code #>} path extraction {@code (<jsonb> #> <path>)} (§3.3). The path is a {@code text[]} bind
 * ({@code $n::text[]}) — a queried/auth path (whose user-key segments may contain {@code , } &#123; &#125; " ;}) is NEVER
 * inlined as a {@code '{...}'} literal. Rendered parenthesized so a following {@code ?|}/{@code @>} binds to the whole
 * extraction.
 */
final class JsonbExtractPath implements SqlExpression {

    private final SqlExpression jsonb;
    private final SqlExpression path;

    JsonbExtractPath(final SqlExpression jsonb, final SqlExpression path) {
        this.jsonb = jsonb;
        this.path = path;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql("(").render(jsonb).sql(" #> ").render(path).sql(")");
    }

}
