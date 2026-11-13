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

import java.util.regex.Pattern;

/**
 * The escape hatch for a fixed SQL snippet the structured nodes do not model (e.g. {@code ON true}, {@code NULLS
 * FIRST}). It is for <strong>compile-time-constant SQL only</strong>: the constructor asserts the fragment carries no
 * value/quote/statement-boundary syntax, so it can never become an injection vector even if a caller mistakenly routes
 * data through it.
 * <p>
 * Rejected characters/sequences: single quotes ({@code '}), the {@code $} bind sigil, statement terminators
 * ({@code ;}), and comment introducers ({@code --}, {@code /*}). A caller needing a value must use a {@link Param} bind;
 * a caller needing an identifier must use {@link Column}/a function — both are separately guarded.
 */
final class RawFragment implements SqlExpression {

    private static final Pattern FORBIDDEN = Pattern.compile("['$;]|--|/\\*");

    private final String sql;

    RawFragment(final String sql) {
        if (sql == null) {
            throw new NullPointerException("RawFragment sql must not be null");
        }
        if (FORBIDDEN.matcher(sql).find()) {
            throw new IllegalArgumentException("RawFragment must be constant SQL free of quotes, '$', ';' and comment "
                    + "introducers — it received <" + sql + ">. Values must be bound as $n Params, not raw fragments.");
        }
        this.sql = sql;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.sql(sql);
    }

}
