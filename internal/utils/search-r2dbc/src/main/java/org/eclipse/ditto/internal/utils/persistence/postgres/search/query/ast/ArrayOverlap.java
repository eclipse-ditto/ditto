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
 * The array-overlap test {@code <array> && <textArray>} (§3.3 global read): true when the two arrays share ANY
 * element. Used for {@code st.global_read && $subjects::text[]} — the GIN-served replacement for Mongo's
 * {@code Filters.in("gr", subjects)}. The subject array is a {@code $n::text[]} bind, so subjects never reach SQL text.
 */
final class ArrayOverlap implements SqlExpression {

    private final SqlExpression left;
    private final SqlExpression right;

    ArrayOverlap(final SqlExpression left, final SqlExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(left).sql(" && ").render(right);
    }

}
