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

import java.math.BigDecimal;
import java.time.Instant;

import io.r2dbc.spi.Statement;

/**
 * The r2dbc-postgresql type hint of a bind parameter ({@code $n}) produced by the SQL AST renderer.
 * <p>
 * The type does two jobs:
 * <ol>
 *     <li>drives the SQL-text cast suffix appended after the {@code $n} placeholder where PostgreSQL needs it to resolve
 *     an operator — {@code ::text[]} for the {@code #>} / {@code ?|} path/subject arrays (§3.3), {@code ::jsonb} for the
 *     {@code @>} containment operand, {@code ::int[]} for numeric-position arrays;</li>
 *     <li>selects the r2dbc {@link Statement} bind call (typed {@code bindNull} for a {@code null} value so the driver
 *     still sends the correct column type).</li>
 * </ol>
 * The placeholder is ALWAYS a {@code $n} bind — a value never reaches SQL text — so hostile strings can only ever land
 * here as bound values, never as SQL syntax.
 */
public enum SqlBindType {

    /** UTF-8 text. */
    TEXT(String.class, ""),
    /** A {@code text[]} array — jsonb {@code #>} paths and {@code ?|} subject arrays (§3.3) bind here. */
    TEXT_ARRAY(String[].class, "::text[]"),
    /** A jsonb document, bound as its String form with a {@code ::jsonb} cast — the {@code @>} operand. */
    JSONB(String.class, "::jsonb"),
    /** An arbitrary-precision number (BSON int/long/double unify onto {@code numeric}, §3.5). */
    NUMERIC(BigDecimal.class, ""),
    /** A {@code numeric[]} array — homogeneous numeric {@code in(path, …)} lists (§3.5). */
    NUMERIC_ARRAY(BigDecimal[].class, "::numeric[]"),
    /** A boolean. */
    BOOLEAN(Boolean.class, ""),
    /** A {@code boolean[]} array — homogeneous boolean {@code in(path, …)} lists (§3.5). */
    BOOLEAN_ARRAY(Boolean[].class, "::boolean[]"),
    /** A 64-bit integer ({@code bigint}) — revisions, {@code LIMIT}/{@code OFFSET}. */
    BIGINT(Long.class, ""),
    /** A timestamp with time zone. */
    TIMESTAMPTZ(Instant.class, ""),
    /** A 32-bit integer. */
    INTEGER(Integer.class, ""),
    /** An {@code int[]} array. */
    INTEGER_ARRAY(Integer[].class, "::int[]");

    private final Class<?> javaType;
    private final String castSuffix;

    SqlBindType(final Class<?> javaType, final String castSuffix) {
        this.javaType = javaType;
        this.castSuffix = castSuffix;
    }

    /**
     * @return the SQL cast to append after the {@code $n} placeholder (e.g. {@code "::text[]"}), or {@code ""} when the
     * driver infers the type from the bound value.
     */
    public String castSuffix() {
        return castSuffix;
    }

    /**
     * @return the Java type used for a typed {@code bindNull} so the driver sends the correct column type for a
     * {@code null} value.
     */
    public Class<?> javaType() {
        return javaType;
    }

    /**
     * Applies a single bind to an r2dbc {@link Statement} at the given 0-based index ({@code $1} = index 0), using a
     * typed {@code bindNull} for a {@code null} value.
     *
     * @param statement the statement to bind onto.
     * @param zeroBasedIndex the 0-based bind index.
     * @param value the value to bind; may be {@code null}.
     */
    public void bind(final Statement statement, final int zeroBasedIndex, final Object value) {
        if (value == null) {
            statement.bindNull(zeroBasedIndex, javaType);
        } else {
            statement.bind(zeroBasedIndex, value);
        }
    }

}
