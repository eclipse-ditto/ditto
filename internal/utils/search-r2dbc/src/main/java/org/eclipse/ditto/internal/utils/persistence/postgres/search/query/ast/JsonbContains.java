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
 * The jsonb {@code @>} containment test {@code <jsonb> @> <jsonb>} (e.g. the policy-fan-out {@code referenced_policies
 * @> '[{"id":…}]'} probe). The right operand is a {@code $n::jsonb} bind — the containment document is never inlined.
 */
final class JsonbContains implements SqlExpression {

    private final SqlExpression left;
    private final SqlExpression right;

    JsonbContains(final SqlExpression left, final SqlExpression right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(left).sql(" @> ").render(right);
    }

}
