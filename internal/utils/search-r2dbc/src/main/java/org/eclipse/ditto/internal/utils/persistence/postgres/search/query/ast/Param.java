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

import javax.annotation.Nullable;

/**
 * A bind parameter — the ONLY leaf through which a value (RQL predicate value, jsonb path array, LIKE pattern, subject
 * array, …) enters a statement. It renders as the next {@code $n} placeholder and records its value + type on the
 * context; the value never touches SQL text.
 * <p>
 * The positional {@code $n} protocol has no named-parameter reuse, so a value used in several places (e.g. the subjects
 * array across an auth tree) is simply a {@code Param} at each position, each binding the same value — deterministic and
 * injection-proof.
 */
final class Param implements SqlExpression {

    @Nullable
    private final Object value;
    private final SqlBindType type;

    Param(@Nullable final Object value, final SqlBindType type) {
        this.value = value;
        if (type == null) {
            throw new NullPointerException("Param type must not be null");
        }
        this.type = type;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.bind(value, type);
    }

}
