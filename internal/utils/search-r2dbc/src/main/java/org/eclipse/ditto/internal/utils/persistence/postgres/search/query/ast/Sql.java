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
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * The public factory facade of the parameterized SQL AST — the single entry point the RQL→SQL visitors (Phases D2/D3)
 * and the read persistence (D4) use to build query trees, keeping the concrete node classes package-internal.
 * <p>
 * <strong>Injection discipline (the reason this facade exists):</strong> the only ways text enters a statement are (a)
 * the fixed grammar baked into the nodes, (b) validated bare identifiers passed to {@link #col}/{@link #func}/the
 * {@link Select} builder, and (c) the {@code assert}-guarded {@link #raw} fragment. EVERY value — RQL predicate values,
 * jsonb path arrays, LIKE patterns, authorization subjects — goes through a typed {@code param(...)} and lands as a
 * {@code $n} bind, never as SQL text.
 */
public final class Sql {

    private Sql() {
        throw new AssertionError();
    }

    /**
     * Renders a node into parameterized SQL + ordered binds.
     *
     * @param node the AST root.
     * @return the rendered SQL and binds.
     */
    public static RenderedSql render(final SqlNode node) {
        return SqlNode.render(node);
    }

    // ---- leaves: columns, literals, raw ---------------------------------------------------------------------------

    /**
     * @param name a bare column identifier (validated).
     * @return an unqualified column reference.
     */
    public static SqlExpression col(final String name) {
        return new Column(null, name);
    }

    /**
     * @param qualifier a bare table-alias identifier (validated).
     * @param name a bare column identifier (validated).
     * @return a qualified column reference {@code qualifier.name}.
     */
    public static SqlExpression col(final String qualifier, final String name) {
        return new Column(qualifier, name);
    }

    /**
     * The {@code TRUE}/{@code FALSE} keyword literal (fixed grammar — carries no data).
     *
     * @param value the boolean.
     * @return the literal.
     */
    public static SqlExpression boolLiteral(final boolean value) {
        return value ? Literal.TRUE : Literal.FALSE;
    }

    /**
     * @return the {@code NULL} keyword literal.
     */
    public static SqlExpression nullLiteral() {
        return Literal.NULL;
    }

    /**
     * A constant SQL fragment (escape hatch). The constructor rejects quotes/{@code $}/{@code ;}/comment introducers, so
     * only compile-time-constant SQL can pass — never a value.
     *
     * @param constantSql the constant SQL text.
     * @return the raw fragment.
     */
    public static SqlExpression raw(final String constantSql) {
        return new RawFragment(constantSql);
    }

    // ---- typed bind parameters ------------------------------------------------------------------------------------

    /**
     * @param value the value; may be {@code null}.
     * @param type the r2dbc type hint.
     * @return a {@code $n} bind parameter.
     */
    public static SqlExpression param(@Nullable final Object value, final SqlBindType type) {
        return new Param(value, type);
    }

    /** @param value UTF-8 text (nullable). @return a {@code text} bind. */
    public static SqlExpression text(@Nullable final String value) {
        return new Param(value, SqlBindType.TEXT);
    }

    /** @param values the array elements. @return a {@code $n::text[]} bind (jsonb paths / subject arrays / IN lists). */
    public static SqlExpression textArray(final String... values) {
        return new Param(values.clone(), SqlBindType.TEXT_ARRAY);
    }

    /** @param values the array elements. @return a {@code $n::text[]} bind. */
    public static SqlExpression textArray(final List<String> values) {
        return new Param(values.toArray(new String[0]), SqlBindType.TEXT_ARRAY);
    }

    /** @param json the jsonb document as text (nullable). @return a {@code $n::jsonb} bind. */
    public static SqlExpression jsonb(@Nullable final String json) {
        return new Param(json, SqlBindType.JSONB);
    }

    /** @param value the number (nullable). @return a {@code numeric} bind. */
    public static SqlExpression numeric(@Nullable final BigDecimal value) {
        return new Param(value, SqlBindType.NUMERIC);
    }

    /** @param value the number. @return a {@code numeric} bind. */
    public static SqlExpression numeric(final long value) {
        return new Param(BigDecimal.valueOf(value), SqlBindType.NUMERIC);
    }

    /** @param value the number. @return a {@code numeric} bind. */
    public static SqlExpression numeric(final double value) {
        return new Param(BigDecimal.valueOf(value), SqlBindType.NUMERIC);
    }

    /** @param values the array elements. @return a {@code $n::numeric[]} bind (homogeneous numeric {@code in} lists). */
    public static SqlExpression numericArray(final List<BigDecimal> values) {
        return new Param(values.toArray(new BigDecimal[0]), SqlBindType.NUMERIC_ARRAY);
    }

    /** @param value the boolean (nullable). @return a {@code boolean} bind. */
    public static SqlExpression bool(@Nullable final Boolean value) {
        return new Param(value, SqlBindType.BOOLEAN);
    }

    /** @param values the array elements. @return a {@code $n::boolean[]} bind (homogeneous boolean {@code in} lists). */
    public static SqlExpression boolArray(final List<Boolean> values) {
        return new Param(values.toArray(new Boolean[0]), SqlBindType.BOOLEAN_ARRAY);
    }

    /** @param value the 64-bit integer. @return a {@code bigint} bind. */
    public static SqlExpression bigint(final long value) {
        return new Param(value, SqlBindType.BIGINT);
    }

    /** @param value the 32-bit integer. @return an {@code integer} bind. */
    public static SqlExpression integer(final int value) {
        return new Param(value, SqlBindType.INTEGER);
    }

    /** @param values the array elements. @return an {@code $n::int[]} bind. */
    public static SqlExpression intArray(final Integer... values) {
        return new Param(values.clone(), SqlBindType.INTEGER_ARRAY);
    }

    /** @param value the timestamp (nullable). @return a {@code timestamptz} bind. */
    public static SqlExpression timestamptz(@Nullable final Instant value) {
        return new Param(value, SqlBindType.TIMESTAMPTZ);
    }

    // ---- comparisons ----------------------------------------------------------------------------------------------

    /** @return {@code left <op> right}. */
    public static SqlExpression compare(final SqlExpression left, final ComparisonOperator operator,
            final SqlExpression right) {
        return new Comparison(left, operator, right);
    }

    /** @return {@code left = right}. */
    public static SqlExpression eq(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.EQ, right);
    }

    /** @return {@code left <> right}. */
    public static SqlExpression ne(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.NE, right);
    }

    /** @return {@code left < right}. */
    public static SqlExpression lt(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.LT, right);
    }

    /** @return {@code left <= right}. */
    public static SqlExpression le(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.LE, right);
    }

    /** @return {@code left > right}. */
    public static SqlExpression gt(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.GT, right);
    }

    /** @return {@code left >= right}. */
    public static SqlExpression ge(final SqlExpression left, final SqlExpression right) {
        return new Comparison(left, ComparisonOperator.GE, right);
    }

    /** @return {@code left = ANY(array)} — the {@code in(path, …)} shape. */
    public static SqlExpression eqAny(final SqlExpression left, final SqlExpression array) {
        return new EqAny(left, array);
    }

    // ---- boolean tree ---------------------------------------------------------------------------------------------

    /** @return {@code (a AND b AND …)} (single operand renders bare). */
    public static SqlExpression and(final SqlExpression... operands) {
        return new And(Arrays.asList(operands));
    }

    /** @return {@code (a AND b AND …)}. */
    public static SqlExpression and(final List<SqlExpression> operands) {
        return new And(operands);
    }

    /** @return {@code (a OR b OR …)} (single operand renders bare). */
    public static SqlExpression or(final SqlExpression... operands) {
        return new Or(Arrays.asList(operands));
    }

    /** @return {@code (a OR b OR …)}. */
    public static SqlExpression or(final List<SqlExpression> operands) {
        return new Or(operands);
    }

    /** @return {@code NOT operand}. */
    public static SqlExpression not(final SqlExpression operand) {
        return new Not(operand);
    }

    // ---- like / null / exists -------------------------------------------------------------------------------------

    /** @return {@code left LIKE pattern ESCAPE '\'}. */
    public static SqlExpression like(final SqlExpression left, final SqlExpression pattern) {
        return new Like(left, pattern, false, null);
    }

    /** @return {@code left [COLLATE "c"] LIKE pattern ESCAPE '\'}. */
    public static SqlExpression like(final SqlExpression left, final SqlExpression pattern,
            @Nullable final String collation) {
        return new Like(left, pattern, false, collation);
    }

    /** @return {@code left [COLLATE "c"] ILIKE pattern ESCAPE '\'}. */
    public static SqlExpression ilike(final SqlExpression left, final SqlExpression pattern,
            @Nullable final String collation) {
        return new Like(left, pattern, true, collation);
    }

    /** @return {@code operand IS NULL}. */
    public static SqlExpression isNull(final SqlExpression operand) {
        return new NullCheck(operand, false);
    }

    /** @return {@code operand IS NOT NULL}. */
    public static SqlExpression isNotNull(final SqlExpression operand) {
        return new NullCheck(operand, true);
    }

    /** @return {@code EXISTS (subquery)}. */
    public static SqlExpression exists(final Select subquery) {
        return new Exists(subquery, false);
    }

    /** @return {@code NOT EXISTS (subquery)}. */
    public static SqlExpression notExists(final Select subquery) {
        return new Exists(subquery, true);
    }

    /**
     * @param subquery a {@code SELECT} returning one row with one column.
     * @return the scalar subquery {@code (<subquery>)}.
     */
    public static SqlExpression scalar(final Select subquery) {
        return new ScalarSubquery(subquery);
    }

    // ---- jsonb operators ------------------------------------------------------------------------------------------

    /** @return {@code (jsonb #> path)} — {@code path} must be a {@code text[]} bind. */
    public static SqlExpression jsonbExtractPath(final SqlExpression jsonb, final SqlExpression path) {
        return new JsonbExtractPath(jsonb, path);
    }

    /** @return {@code (jsonb #>> path)} — text extraction (§3.5 group-by); {@code path} must be a {@code text[]} bind. */
    public static SqlExpression jsonbExtractText(final SqlExpression jsonb, final SqlExpression path) {
        return new JsonbExtractText(jsonb, path);
    }

    /** @return {@code jsonb ?| keys} — {@code keys} must be a {@code text[]} bind. */
    public static SqlExpression jsonbAnyKeyMatch(final SqlExpression jsonb, final SqlExpression keys) {
        return new JsonbAnyKeyMatch(jsonb, keys);
    }

    /** @return {@code jsonb ? key}. */
    public static SqlExpression jsonbKeyExists(final SqlExpression jsonb, final SqlExpression key) {
        return new JsonbKeyExists(jsonb, key);
    }

    /** @return {@code left && right} — array overlap ({@code st.global_read && $subjects::text[]}, §3.3). */
    public static SqlExpression arrayOverlap(final SqlExpression left, final SqlExpression right) {
        return new ArrayOverlap(left, right);
    }

    /** @return {@code left @> right} — {@code right} must be a {@code jsonb} bind. */
    public static SqlExpression jsonbContains(final SqlExpression left, final SqlExpression right) {
        return new JsonbContains(left, right);
    }

    // ---- coalesce / case / function -------------------------------------------------------------------------------

    /** @return {@code COALESCE(a, b, …)} (≥ 2 arguments). */
    public static SqlExpression coalesce(final SqlExpression... arguments) {
        return new Coalesce(Arrays.asList(arguments));
    }

    /** @return {@code COALESCE(a, b, …)}. */
    public static SqlExpression coalesce(final List<SqlExpression> arguments) {
        return new Coalesce(arguments);
    }

    /**
     * @param condition the {@code WHEN} condition.
     * @param result the {@code THEN} result.
     * @return one {@code CASE} branch.
     */
    public static CaseBranch when(final SqlExpression condition, final SqlExpression result) {
        return new CaseBranch(condition, result);
    }

    /** @return {@code CASE WHEN … THEN … [ELSE else] END}. */
    public static SqlExpression caseWhen(final List<CaseBranch> branches, @Nullable final SqlExpression elseResult) {
        return new Case(branches, elseResult);
    }

    /** @return {@code name(arg1, arg2, …)} — {@code name} is a validated identifier. */
    public static SqlExpression func(final String name, final SqlExpression... arguments) {
        return new FunctionCall(name, Arrays.asList(arguments));
    }

    /** @return {@code name(args…)}. */
    public static SqlExpression func(final String name, final List<SqlExpression> arguments) {
        return new FunctionCall(name, arguments);
    }

    // ---- select ---------------------------------------------------------------------------------------------------

    /**
     * @return a new {@code SELECT} builder.
     */
    public static Select.Builder select() {
        return Select.builder();
    }

}
