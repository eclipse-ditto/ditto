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

import java.util.function.Function;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;

/**
 * The SQL counterpart of Mongo's {@code Function<String, Bson>} predicate closure (CreateBsonPredicateVisitor): a
 * resolved comparison the field visitors turn into a flat-table probe or a doc-row column condition.
 * <p>
 * It carries two renderings because Postgres has two field worlds (unlike Mongo's uniform dotted paths):
 * <ul>
 *   <li>{@link #flatCondition(String)} — the value condition on a {@code search_flat} row alias's typed value columns,
 *       used inside an {@code EXISTS} probe over a {@code wpath};</li>
 *   <li>{@link #columnCondition(SqlExpression)} — the condition on a doc-row column, used for the two ROOT-mapped
 *       system fields ({@code _id}/{@code thing_id}, {@code _namespace}/{@code namespace}) which have no flat probe and
 *       no per-field auth (§3.3 exception 1). Those columns are never null and never missing, so this rendering is
 *       complete on its own; the flags below shape only the flat probe.</li>
 * </ul>
 * Three flags tell the flat/wildcard field visitors how to wrap {@link #flatCondition(String)}:
 * <ul>
 *   <li>{@link #scopedNegation()} marks {@code ne(...)}: Mongo emits {@code and(ne, exists)} (multikey "no element
 *       equals"), which the field visitors realize as {@code EXISTS(wpath) ∧ NOT EXISTS(wpath ∧ value=)} — so
 *       {@link #flatCondition(String)} of a {@code ne} predicate is the equality that gets NEGATED, not the match
 *       itself;</li>
 *   <li>{@link #matchesMissing()} marks a {@code null} operand Mongo answers WITHOUT an {@code exists} clause
 *       ({@code in(…, null)}, {@code ge(null)}, {@code le(null)}, a placeholder that resolved to null in {@code eq}):
 *       {@code {f: null}} matches a BSON null AND an absent field, so the field visitors OR a {@code NOT EXISTS(wpath)}
 *       (per feature on a {@code features/*} path) onto the positive probe. The literal {@code eq(null)} does NOT
 *       carry it — Mongo adds {@code exists} there, a null-rank row must exist;</li>
 *   <li>{@link #neverMatches()} marks {@code gt(null)} / {@code lt(null)} and an {@code in} with no values: nothing is
 *       strictly ordered against null in MongoDB and {@code $in: []} matches nothing, so the field visitors render
 *       {@code false} and skip probe and auth recheck alike.</li>
 * </ul>
 */
final class SqlPredicate {

    private static final Function<String, SqlExpression> FLAT_FALSE = alias -> Sql.boolLiteral(false);
    private static final Function<SqlExpression, SqlExpression> COLUMN_FALSE = column -> Sql.boolLiteral(false);

    private final Function<String, SqlExpression> flatCondition;
    private final Function<SqlExpression, SqlExpression> columnCondition;
    private final boolean scopedNegation;
    private final boolean matchesMissing;
    private final boolean neverMatches;

    private SqlPredicate(final Function<String, SqlExpression> flatCondition,
            final Function<SqlExpression, SqlExpression> columnCondition,
            final boolean scopedNegation,
            final boolean matchesMissing,
            final boolean neverMatches) {
        this.flatCondition = flatCondition;
        this.columnCondition = columnCondition;
        this.scopedNegation = scopedNegation;
        this.matchesMissing = matchesMissing;
        this.neverMatches = neverMatches;
    }

    /**
     * @param flatCondition the positive value match on a flat row alias.
     * @param columnCondition the full condition on a doc-row column.
     * @return a plain predicate: the flat probe is {@code EXISTS(wpath ∧ flatCondition)}.
     */
    static SqlPredicate of(final Function<String, SqlExpression> flatCondition,
            final Function<SqlExpression, SqlExpression> columnCondition) {
        return new SqlPredicate(flatCondition, columnCondition, false, false, false);
    }

    /**
     * @param flatCondition the equality the field visitors NEGATE ({@code ne}).
     * @param columnCondition the full {@code <>} / {@code IS NOT NULL} condition on a doc-row column.
     * @return a {@code ne(...)} predicate: the flat probe is {@code EXISTS(wpath) ∧ NOT EXISTS(wpath ∧ flatCondition)}.
     */
    static SqlPredicate scopedNegation(final Function<String, SqlExpression> flatCondition,
            final Function<SqlExpression, SqlExpression> columnCondition) {
        return new SqlPredicate(flatCondition, columnCondition, true, false, false);
    }

    /**
     * @param flatCondition the positive value match on a flat row alias (incl. the null-rank row).
     * @param columnCondition the full condition on a doc-row column.
     * @return a null-operand predicate without Mongo's {@code exists}: the flat probe is
     * {@code EXISTS(wpath ∧ flatCondition) ∨ NOT EXISTS(wpath)}.
     */
    static SqlPredicate matchingMissing(final Function<String, SqlExpression> flatCondition,
            final Function<SqlExpression, SqlExpression> columnCondition) {
        return new SqlPredicate(flatCondition, columnCondition, false, true, false);
    }

    /**
     * @return the predicate that matches nothing ({@code gt(null)}, {@code lt(null)}, an {@code in} with no values):
     * {@code false} in both renderings, no probe.
     */
    static SqlPredicate never() {
        return new SqlPredicate(FLAT_FALSE, COLUMN_FALSE, false, false, true);
    }

    /**
     * @param flatAlias the alias of the {@code search_flat} row the value columns belong to.
     * @return the value condition on that row (the positive match, or the equality-to-negate for {@code ne}; a bare
     * {@code false} for {@link #never()}). For a {@link #matchesMissing()} predicate it is the value-or-null-rank part
     * ONLY — the caller must OR a {@code NOT EXISTS(wpath)} probe onto its {@code EXISTS} (per feature on a wildcard
     * path), as {@code GetFilterSqlVisitor} does; rendered inside a lone {@code EXISTS} it would drop every thing
     * whose field is absent.
     */
    SqlExpression flatCondition(final String flatAlias) {
        return flatCondition.apply(flatAlias);
    }

    /**
     * @param column the doc-row column expression.
     * @return the full condition on that column (already includes {@code <>} / {@code IS NULL} where applicable).
     */
    SqlExpression columnCondition(final SqlExpression column) {
        return columnCondition.apply(column);
    }

    /**
     * @return {@code true} for {@code ne(...)} — the flat/wildcard probe must be {@code EXISTS ∧ NOT EXISTS(value=)}.
     */
    boolean scopedNegation() {
        return scopedNegation;
    }

    /**
     * @return {@code true} for a null operand without Mongo's {@code exists} — the flat/wildcard probe must also accept
     * a path (per feature on a wildcard path) that has NO row at all.
     */
    boolean matchesMissing() {
        return matchesMissing;
    }

    /**
     * @return {@code true} for {@code gt(null)} / {@code lt(null)} / an {@code in} with no values — the field visitors
     * render {@code false}.
     */
    boolean neverMatches() {
        return neverMatches;
    }
}
