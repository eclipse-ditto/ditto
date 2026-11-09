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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query;

import java.math.BigDecimal;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;

/**
 * RQL-value → typed flat-column routing (plan §3.5 type coercion): numbers probe {@code val_num} (BSON int/long/double
 * unify onto {@code numeric}), booleans {@code val_bool}, everything else (strings, resolved time placeholders)
 * {@code val_text} (compared under the column's default {@code COLLATE "C"}). Cross-type comparisons are therefore
 * type-bracketed exactly like Mongo — a numeric predicate can only match a numeric leaf, never a string leaf.
 * <p>
 * A {@code null} operand has no typed bind: {@code CreateSqlPredicateVisitor} dispatches every null operand (literal
 * or a placeholder resolved to null) onto the {@code type_rank} / missing-row forms before it gets here, so
 * {@link #valueColumn} and {@link #bind} reject {@code null} instead of rendering the text {@code 'null'}, which would
 * silently compare a string against the word "null".
 * </p>
 */
final class SqlValues {

    static final String VAL_NUM = "val_num";
    static final String VAL_TEXT = "val_text";
    static final String VAL_BOOL = "val_bool";

    private SqlValues() {
        throw new AssertionError("no instances");
    }

    /**
     * @param value a resolved, non-null RQL value (never a {@code ParsedPlaceholder}).
     * @return the {@code search_flat} value column that stores this value's type.
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    static String valueColumn(final Object value) {
        requireOperand(value);
        if (value instanceof Boolean) {
            return VAL_BOOL;
        } else if (value instanceof Number) {
            return VAL_NUM;
        } else {
            return VAL_TEXT;
        }
    }

    /**
     * @param value a resolved, non-null RQL value.
     * @return the typed {@code $n} bind for this value (numeric/boolean/text), preserving int/long/double exactly.
     * @throws IllegalArgumentException if {@code value} is {@code null}.
     */
    static SqlExpression bind(final Object value) {
        requireOperand(value);
        if (value instanceof Boolean b) {
            return Sql.bool(b);
        } else if (value instanceof BigDecimal bd) {
            return Sql.numeric(bd);
        } else if (value instanceof Number n) {
            if (isIntegral(n)) {
                return Sql.numeric(n.longValue());
            }
            return Sql.numeric(n.doubleValue());
        } else {
            return Sql.text(String.valueOf(value));
        }
    }

    /**
     * @param value a resolved RQL value.
     * @return the value as a {@link BigDecimal} (for numeric {@code in} arrays), preserving int/long/double exactly.
     */
    static BigDecimal toBigDecimal(final Number value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        } else if (isIntegral(value)) {
            return BigDecimal.valueOf(value.longValue());
        }
        return BigDecimal.valueOf(value.doubleValue());
    }

    private static boolean isIntegral(final Number n) {
        return n instanceof Long || n instanceof Integer || n instanceof Short || n instanceof Byte;
    }

    private static void requireOperand(final Object value) {
        if (value == null) {
            throw new IllegalArgumentException("A null RQL operand has no typed bind; CreateSqlPredicateVisitor must "
                    + "dispatch it onto the type_rank / missing-row forms before binding");
        }
    }
}
