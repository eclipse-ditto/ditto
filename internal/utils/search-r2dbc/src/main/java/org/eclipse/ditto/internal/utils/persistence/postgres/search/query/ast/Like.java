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

import javax.annotation.Nullable;

/**
 * A {@code LIKE}/{@code ILIKE} pattern match with an explicit {@code ESCAPE '\'} and an optional {@code COLLATE}
 * override on the left operand (§3.5): {@code val_text LIKE $p ESCAPE '\'} for a {@code like} that is prefix-only or
 * has no run of three consecutive literal letters/digits, {@code val_text COLLATE "C.utf8" LIKE $p ESCAPE '\'} for
 * every other {@code like}, and
 * {@code val_text COLLATE "C.utf8" ILIKE $p ESCAPE '\'} for {@code ilike} (the collation matches the {@code sf_trgm}
 * expression index — the only form it can serve — and, for {@code ilike}, restores non-ASCII case folding, §3.2). The
 * pattern is ALWAYS a bind — user wildcard text never reaches SQL.
 */
final class Like implements SqlExpression {

    /** A collation name is a bare identifier optionally dotted (e.g. {@code C}, {@code C.utf8}); rendered double-quoted. */
    private static final Pattern COLLATION = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private final SqlExpression left;
    private final SqlExpression pattern;
    private final boolean caseInsensitive;
    @Nullable
    private final String collation;

    Like(final SqlExpression left, final SqlExpression pattern, final boolean caseInsensitive,
            @Nullable final String collation) {
        this.left = left;
        this.pattern = pattern;
        this.caseInsensitive = caseInsensitive;
        if (collation != null && !COLLATION.matcher(collation).matches()) {
            throw new IllegalArgumentException("Illegal COLLATE name <" + collation + ">");
        }
        this.collation = collation;
    }

    @Override
    public void render(final SqlRenderContext ctx) {
        ctx.render(left);
        if (collation != null) {
            ctx.sql(" COLLATE \"").sql(collation).sql("\"");
        }
        ctx.sql(caseInsensitive ? " ILIKE " : " LIKE ").render(pattern).sql(" ESCAPE '\\'");
    }

}
