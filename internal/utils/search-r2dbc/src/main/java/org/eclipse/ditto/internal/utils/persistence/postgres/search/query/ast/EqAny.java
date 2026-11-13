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
 * An {@code left = ANY(<array>)} membership test — the {@code in(path, v1..vn)} shape (§3.5). The array is a single bind
 * ({@code $n::text[]} / {@code ::int[]}), so an arbitrary-length {@code in} list is one parameter, not n inlined values.
 */
final class EqAny implements SqlExpression {

    private final SqlExpression left;
    private final SqlExpression array;

    EqAny(final SqlExpression left, final SqlExpression array) {
        this.left = left;
        this.array = array;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(left).sql(" = ANY(").render(array).sql(")");
    }

}
