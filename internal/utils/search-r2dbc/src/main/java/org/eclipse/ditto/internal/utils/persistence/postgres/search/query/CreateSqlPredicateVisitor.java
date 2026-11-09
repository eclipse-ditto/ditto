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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.ComparisonOperator;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.placeholders.PlaceholderResolver;
import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;

/**
 * The SQL counterpart of {@code CreateBsonPredicateVisitor} (thingsearch/service): turns an RQL {@code Predicate} into a
 * {@link SqlPredicate}. Value resolution mirrors {@code CreateBsonPredicateVisitor.resolveValue} exactly (the
 * {@code TimePlaceholder} chain, §3.4/§3.5) — a {@code ParsedPlaceholder} is resolved to its concrete value BEFORE it
 * ever becomes a bind. The collation used by string comparisons is the flat column's default {@code COLLATE "C"};
 * {@code ilike} and a trigram-bearing {@code like} match under {@code C.utf8}, the collation of the {@code sf_trgm}
 * index (see {@link #visitLikeWithWildcards}).
 * <p>
 * <b>Side-by-side comment map (Mongo class:line → this method):</b>
 * <ul>
 *   <li>{@code visitEq:101-108} (incl. the {@code eq(null)} = {@code and(eq(null), exists)} special) →
 *       {@link #visitEq}; a placeholder that resolves to null is NOT that special case on Mongo (it resolves after
 *       the null check and yields a bare {@code {f: null}}: null OR missing) → the
 *       {@link SqlPredicate#matchingMissing} form</li>
 *   <li>{@code visitGe/Gt/Le/Lt:110-171} → {@link #visitGe}/{@link #visitGt}/{@link #visitLe}/{@link #visitLt}; a null
 *       operand: {@code $gte/$lte: null} is {@code {f: null}} (null OR missing, no {@code exists}) →
 *       {@link SqlPredicate#matchingMissing}; {@code $gt/$lt: null} matches nothing → {@link SqlPredicate#never}</li>
 *   <li>{@code visitNe:173-176} ({@code and(ne, exists)}, incl. the {@code ne(null)} null special-case mirroring
 *       {@code visitEq}'s — also for a placeholder resolved to null) → {@link #visitNe}</li>
 *   <li>{@code visitIn:120-123} → {@link #visitIn}; a null member ({@code $in: […, null]}, no {@code exists}) matches
 *       the listed values, a BSON null AND a missing field → the null is stripped from the value groups, a
 *       {@code type_rank = TYPE_NULL} group is added and the predicate takes the {@link SqlPredicate#matchingMissing}
 *       form; on a root-mapped column (never null) only the values remain; an {@code in} with no values at all
 *       ({@code $in: []} matches nothing; the RQL grammar cannot spell it) → {@link SqlPredicate#never}</li>
 *   <li>{@code visitLike/visitILike:130-151} — regex path bypassed; the wildcard overrides build native LIKE
 *       (A6 {@code visitLikeWithWildcards}) → {@link #visitLikeWithWildcards}/{@link #visitILikeWithWildcards}</li>
 *   <li>{@code resolveValue:178-191} → {@link #resolveValue}, called BEFORE every null dispatch here (Mongo's
 *       {@code visitEq} checks the raw value first — see the first bullet)</li>
 * </ul>
 * A null operand never reaches {@link SqlValues#bind} / {@link SqlValues#valueColumn} (both reject it).
 */
final class CreateSqlPredicateVisitor implements PredicateVisitor<SqlPredicate> {

    private static final String TRIGRAM_INDEX_COLLATION = PostgresSearchSchema.TRIGRAM_INDEX_COLLATION;

    /**
     * The shortest run of literal letters/digits that always yields a pg_trgm trigram, whatever surrounds it: pg_trgm
     * builds trigrams only from word characters, and pads a word only where it is not cut by a wildcard.
     */
    private static final int MIN_TRIGRAM_RUN = 3;

    private final List<PlaceholderResolver<?>> additionalPlaceholderResolvers;

    CreateSqlPredicateVisitor(final Collection<PlaceholderResolver<?>> additionalPlaceholderResolvers) {
        this.additionalPlaceholderResolvers = List.copyOf(additionalPlaceholderResolvers);
    }

    @Override
    public SqlPredicate visitEq(@Nullable final Object value) {
        if (value == null) {
            // eq(null): a null-rank (type_rank = TYPE_NULL) flat row exists — "missing" does NOT match (Mongo adds
            // exists() to disambiguate; on the flat table a null-rank row *is* an existing row). Column: IS NULL.
            return SqlPredicate.of(CreateSqlPredicateVisitor::nullRank, Sql::isNull);
        }
        final Object resolved = resolveValue(value);
        if (resolved == null) {
            // A placeholder nobody resolves: Mongo resolves AFTER its null check, so this is Filters.eq(f, null) — a
            // bare {f: null} that matches a BSON null AND a missing field, WITHOUT the exists() the literal gets.
            return SqlPredicate.matchingMissing(CreateSqlPredicateVisitor::nullRank, Sql::isNull);
        }
        return comparison(ComparisonOperator.EQ, resolved);
    }

    @Override
    public SqlPredicate visitGe(@Nullable final Object value) {
        return inclusiveBound(ComparisonOperator.GE, resolveValue(value));
    }

    @Override
    public SqlPredicate visitGt(@Nullable final Object value) {
        return exclusiveBound(ComparisonOperator.GT, resolveValue(value));
    }

    @Override
    public SqlPredicate visitLe(@Nullable final Object value) {
        return inclusiveBound(ComparisonOperator.LE, resolveValue(value));
    }

    @Override
    public SqlPredicate visitLt(@Nullable final Object value) {
        return exclusiveBound(ComparisonOperator.LT, resolveValue(value));
    }

    @Override
    public SqlPredicate visitNe(@Nullable final Object value) {
        // resolve FIRST: Mongo's and(ne(f, resolved), exists(f)) with a placeholder resolved to null IS ne(f, null)
        final Object resolved = resolveValue(value);
        if (resolved == null) {
            // ne(null): Mongo parity -- must match ONLY a leaf that EXISTS with a NON-null value (a null-rank row,
            // like "missing", is excluded). Mirrors visitEq's null special-case: negate the SAME
            // type_rank = TYPE_NULL equality instead of falling into the generic bind path below, where a text
            // literal 'null' (String.valueOf(null)) would never equal a null-rank row's SQL-NULL val_text column, so
            // the NOT-EXISTS probe would be vacuously true and the predicate would silently degenerate to a bare
            // exists(). Column form: IS NOT NULL (the root-mapped columns this can reach, thing_id/namespace, are
            // never actually null).
            return SqlPredicate.scopedNegation(CreateSqlPredicateVisitor::nullRank, Sql::isNotNull);
        }
        // ne = and(ne, exists) (Mongo, multikey "no element equals"): flatCondition is the equality that gets NEGATED
        // by the field visitor (EXISTS(wpath) ∧ NOT EXISTS(wpath ∧ value=)); column condition is a direct <>.
        final String valueColumn = SqlValues.valueColumn(resolved);
        return SqlPredicate.scopedNegation(
                alias -> Sql.eq(Sql.col(alias, valueColumn), SqlValues.bind(resolved)),
                column -> Sql.ne(column, Sql.text(String.valueOf(resolved))));
    }

    @Override
    public SqlPredicate visitIn(final List<?> values) {
        final List<Object> resolved = new ArrayList<>(values.size());
        boolean nullMember = false;
        for (final Object v : values) {
            final Object r = resolveValue(v);
            if (r == null) {
                nullMember = true;
            } else {
                resolved.add(r);
            }
        }
        final List<Object> nonNull = List.copyOf(resolved);
        if (nonNull.isEmpty() && !nullMember) {
            // in() with no values (a Criteria built in code; the RQL grammar cannot spell it): Mongo's {$in: []}
            // matches nothing — a bare false, no probe, no bind.
            return SqlPredicate.never();
        }
        if (!nullMember) {
            return SqlPredicate.of(
                    alias -> inFlatCondition(alias, nonNull, false),
                    column -> Sql.eqAny(column, Sql.textArray(toStringList(nonNull))));
        }
        // in(…, null): Mongo's {$in: […, null]} (no exists()) matches the listed values, a BSON null AND a missing
        // field. Flat: the typed value groups OR a null-rank row, and the field visitor adds the missing-row branch.
        // Column: thing_id/namespace are never null, so the null member can match nothing there — only the listed
        // values remain, and none at all is false.
        return SqlPredicate.matchingMissing(
                alias -> inFlatCondition(alias, nonNull, true),
                column -> nonNull.isEmpty()
                        ? Sql.boolLiteral(false)
                        : Sql.eqAny(column, Sql.textArray(toStringList(nonNull))));
    }

    @Override
    public SqlPredicate visitLike(@Nullable final String value) {
        // Bypassed in production: LikePredicateImpl.accept calls visitLikeWithWildcards, which this visitor overrides to
        // build a native SQL LIKE without the intermediate Java regex. Regex→LIKE is not expressible, so reaching here
        // means a caller invoked visitLike directly, which the Postgres translator does not support.
        throw new UnsupportedOperationException(
                "CreateSqlPredicateVisitor supports visitLikeWithWildcards (native LIKE), not the regex visitLike");
    }

    @Override
    public SqlPredicate visitILike(@Nullable final String value) {
        throw new UnsupportedOperationException(
                "CreateSqlPredicateVisitor supports visitILikeWithWildcards (native ILIKE), not the regex visitILike");
    }

    /**
     * Case-sensitive {@code like}. The flat column is {@code COLLATE "C"}, but the {@code sf_trgm} trigram index is on
     * {@code (val_text COLLATE "C.utf8")}, and the planner only uses an index whose collation equals the clause's. So:
     * <ul>
     *   <li>a prefix-only pattern ({@link #isPrefixOnly}) keeps the plain predicate — {@code sf_text (wpath, val_text)}
     *   serves it as a range scan and a low-selectivity trigram scan is not even a candidate;</li>
     *   <li>a pattern without a run of {@value #MIN_TRIGRAM_RUN} consecutive literal letters/digits
     *   ({@link #hasTrigramRun}, e.g. {@code *ab*}, {@code ?????}, {@code *47}) keeps it too: it has no
     *   <em>guaranteed</em> trigram. Unanchored short runs yield none, so {@code sf_trgm} could only be read in full —
     *   a scan the planner mis-costs (generic LIKE estimate) and picks on large paths, several times slower than the
     *   wpath-bounded B-tree plan. Short runs next to the pattern's start or end may yield padded trigrams
     *   ({@code *47} → {@code "47 "}), but those are typically unselective and get the same generic estimate;</li>
     *   <li>every other pattern is matched under {@code C.utf8}, the only form {@code sf_trgm} can serve. A fixed
     *   leading prefix still gets the {@code sf_text} range: PostgreSQL derives it from the B-tree's own {@code "C"}
     *   collation, not from the clause's.</li>
     * </ul>
     * {@code C} and {@code C.utf8} are both deterministic, so the matched rows are the same either way. The root-mapped
     * column form ({@code search_things.thing_id}/{@code namespace}, no trigram index) stays plain.
     */
    @Override
    public SqlPredicate visitLikeWithWildcards(@Nullable final String wildcardExpression) {
        final String pattern = toSqlLikePattern(wildcardExpression);
        final String flatCollation = !isPrefixOnly(wildcardExpression) && hasTrigramRun(wildcardExpression)
                ? TRIGRAM_INDEX_COLLATION
                : null;
        return SqlPredicate.of(
                alias -> Sql.like(Sql.col(alias, SqlValues.VAL_TEXT), Sql.text(pattern), flatCollation),
                column -> Sql.like(column, Sql.text(pattern)));
    }

    @Override
    public SqlPredicate visitILikeWithWildcards(@Nullable final String wildcardExpression) {
        final String pattern = toSqlLikePattern(wildcardExpression);
        return SqlPredicate.of(
                alias -> Sql.ilike(Sql.col(alias, SqlValues.VAL_TEXT), Sql.text(pattern), TRIGRAM_INDEX_COLLATION),
                column -> Sql.ilike(column, Sql.text(pattern), TRIGRAM_INDEX_COLLATION));
    }

    /**
     * {@code ge}/{@code le}: a null operand is Mongo's {@code {$gte|$lte: null}} = {@code {f: null}} (null OR missing).
     */
    private static SqlPredicate inclusiveBound(final ComparisonOperator operator, @Nullable final Object resolved) {
        if (resolved == null) {
            // no exists() on Mongo, so unlike the literal eq(null) the missing field matches too; column: IS NULL
            return SqlPredicate.matchingMissing(CreateSqlPredicateVisitor::nullRank, Sql::isNull);
        }
        return comparison(operator, resolved);
    }

    /** {@code gt}/{@code lt}: nothing is strictly ordered against null on Mongo ({@code {$gt|$lt: null}} = nothing). */
    private static SqlPredicate exclusiveBound(final ComparisonOperator operator, @Nullable final Object resolved) {
        if (resolved == null) {
            return SqlPredicate.never();
        }
        return comparison(operator, resolved);
    }

    private static SqlPredicate comparison(final ComparisonOperator operator, final Object resolved) {
        final String valueColumn = SqlValues.valueColumn(resolved);
        return SqlPredicate.of(
                alias -> Sql.compare(Sql.col(alias, valueColumn), operator, SqlValues.bind(resolved)),
                column -> Sql.compare(column, operator, Sql.text(String.valueOf(resolved))));
    }

    /** The null-rank row condition {@code <alias>.type_rank = TYPE_NULL}: the flat form of a JSON {@code null} leaf. */
    private static SqlExpression nullRank(final String alias) {
        return Sql.eq(Sql.col(alias, "type_rank"), Sql.integer(ThingFlattener.TYPE_NULL));
    }

    /**
     * The flat value condition of {@code in}: one {@code = ANY} group per value type (numbers, texts, booleans) OR-ed,
     * plus the null-rank row when the list had a null member. Never called without at least one group:
     * {@link #visitIn} routes an empty list to {@link SqlPredicate#never}.
     */
    private static SqlExpression inFlatCondition(final String alias, final List<Object> nonNullValues,
            final boolean nullMember) {
        final List<BigDecimal> numbers = new ArrayList<>();
        final List<Boolean> booleans = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        for (final Object v : nonNullValues) {
            if (v instanceof Boolean b) {
                booleans.add(b);
            } else if (v instanceof Number n) {
                numbers.add(SqlValues.toBigDecimal(n));
            } else {
                texts.add(String.valueOf(v));
            }
        }
        final List<SqlExpression> groups = new ArrayList<>();
        if (!numbers.isEmpty()) {
            groups.add(Sql.eqAny(Sql.col(alias, SqlValues.VAL_NUM), Sql.numericArray(numbers)));
        }
        if (!texts.isEmpty()) {
            groups.add(Sql.eqAny(Sql.col(alias, SqlValues.VAL_TEXT), Sql.textArray(texts)));
        }
        if (!booleans.isEmpty()) {
            groups.add(Sql.eqAny(Sql.col(alias, SqlValues.VAL_BOOL), Sql.boolArray(booleans)));
        }
        if (nullMember) {
            groups.add(nullRank(alias));
        }
        return groups.size() == 1 ? groups.get(0) : Sql.or(groups);
    }

    private static List<String> toStringList(final List<Object> resolved) {
        final List<String> strings = new ArrayList<>(resolved.size());
        for (final Object v : resolved) {
            strings.add(String.valueOf(v));
        }
        return strings;
    }

    /**
     * @return whether the RQL wildcard expression is prefix-only: no {@code *}/{@code ?} except a trailing run of
     * {@code *}. A pattern without any wildcard (an equality for LIKE) and a bare {@code *} count as prefix-only.
     */
    private static boolean isPrefixOnly(@Nullable final String wildcardExpression) {
        if (wildcardExpression == null) {
            return true;
        }
        int end = wildcardExpression.length();
        while (end > 0 && wildcardExpression.charAt(end - 1) == '*') {
            end--;
        }
        for (int i = 0; i < end; i++) {
            final char c = wildcardExpression.charAt(i);
            if (c == '*' || c == '?') {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether the RQL wildcard expression contains {@value #MIN_TRIGRAM_RUN} consecutive literal letters/digits
     * (code points; {@code *}, {@code ?} and every other non-word character end a run, as they end a pg_trgm word) —
     * a sufficient condition for at least one unpadded trigram, not a test for "yields no trigram at all".
     */
    private static boolean hasTrigramRun(@Nullable final String wildcardExpression) {
        if (wildcardExpression == null) {
            return false;
        }
        int run = 0;
        for (int i = 0; i < wildcardExpression.length(); ) {
            final int codePoint = wildcardExpression.codePointAt(i);
            run = Character.isLetterOrDigit(codePoint) ? run + 1 : 0;
            if (run >= MIN_TRIGRAM_RUN) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Converts an RQL wildcard expression ({@code *} / {@code ?}) to a SQL LIKE pattern with {@code ESCAPE '\'}:
     * {@code *} → {@code %}, {@code ?} → {@code _}, and a literal {@code %}/{@code _}/{@code \} is backslash-escaped.
     */
    @Nullable
    private static String toSqlLikePattern(@Nullable final String wildcardExpression) {
        if (wildcardExpression == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder(wildcardExpression.length() + 4);
        for (int i = 0; i < wildcardExpression.length(); i++) {
            final char c = wildcardExpression.charAt(i);
            switch (c) {
                case '*' -> sb.append('%');
                case '?' -> sb.append('_');
                case '%', '_', '\\' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Resolves a {@link ParsedPlaceholder} against the additional resolvers (e.g. {@code time:now}); passes any other
     * value through unchanged — a 1:1 transcription of {@code CreateBsonPredicateVisitor.resolveValue}.
     */
    @Nullable
    private Object resolveValue(@Nullable final Object value) {
        if (value instanceof ParsedPlaceholder parsed) {
            final String prefix = parsed.getPrefix();
            final String name = parsed.getName();
            return additionalPlaceholderResolvers.stream()
                    .filter(pr -> prefix.equals(pr.getPrefix()))
                    .filter(pr -> pr.supports(name))
                    .flatMap(pr -> pr.resolveValues(name).stream())
                    .findFirst()
                    .orElse(null);
        }
        return value;
    }
}
