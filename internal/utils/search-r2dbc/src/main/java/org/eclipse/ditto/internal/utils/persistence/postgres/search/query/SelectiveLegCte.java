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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.SqlExpression;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.TimePlaceholder;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.criteria.visitors.CriteriaVisitor;
import org.eclipse.ditto.rql.query.criteria.visitors.PredicateVisitor;
import org.eclipse.ditto.rql.query.expression.ExistsFieldExpression;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.visitors.FilterFieldExpressionVisitor;

/**
 * The selective-leg CTE (plan §3.5). A search {@code WHERE} is a chain of correlated {@code EXISTS} probes over
 * {@code search_flat}, and PostgreSQL keeps statistics per column, not per JSON path, so its estimates for a path are
 * often far off. Staging one selective conjunct as a {@code WITH … AS MATERIALIZED} thing-id set and semi-joining
 * {@code search_things} to it pins the drive order to that leg's index scan.
 * <p>
 * <b>Correctness invariant.</b> The CTE is a pure <em>superset pre-filter</em>: the full translated predicate (auth
 * rechecks included) is still applied unchanged in the main {@code WHERE}, so the CTE changes the plan, never the
 * result. Hence legs come only from top-level {@code AND} conjuncts (or the sole criterion); a leg inside
 * {@code OR}/{@code NOR}/{@code NOT} is not a superset and is never hoisted.
 * </p>
 * <p>
 * <b>Candidates.</b> Only on a flattened, non-wildcard path:
 * </p>
 * <ul>
 *     <li>a <em>point leg</em>: {@code eq(non-null)} or {@code in(non-empty)} without a null member; the first one
 *     counts. A null operand (also a placeholder that resolves to null) matches things with NO row at the path
 *     (MongoDB: null = missing), which no leg built from flat rows can produce;</li>
 *     <li>a <em>range leg</em> (only with {@link SelectiveLegs#POINT_AND_RANGE}): a {@code ge}/{@code gt} and a
 *     {@code le}/{@code lt} with numeric values on the same path; the first path that has both, with its first lower
 *     and first upper bound. The translator renders each bound as its own {@code EXISTS}, and an array is flattened to
 *     several rows of one path, so the two bounds may be met by different elements (MongoDB semantics): a thing with
 *     {@code [91621, 91214, 93186]} matches {@code and(ge(p,91700),le(p,93000))} although no element lies in the
 *     range. The leg is therefore the rows in the range {@code UNION ALL} the rows with {@code ord = 1} at the path
 *     (every thing with a second value there). This is a superset: if two different rows meet the bounds, they share
 *     the thing and path and differ in {@code ord}, and {@code ord} is numbered 0, 1, 2, … per path, so a row with
 *     {@code ord = 1} exists. The {@code ord = 1} rows are served by the system index {@code sf_multi}.</li>
 * </ul>
 * <p>
 * One-sided ranges, non-numeric bounds (so also {@code ge}/{@code gt}/{@code le}/{@code lt} of {@code null}),
 * {@code ne}, {@code like}/{@code ilike}, {@code eq(null)}, {@code in(…, null)}, {@code exists}/{@code empty},
 * root-mapped and {@code features/*} fields are never legs.
 * </p>
 * <p>
 * <b>Choice.</b> A point leg alone is materialised. A range leg alone is never materialised: the statement keeps the
 * plan it had before range legs. Measured on the 1M-thing benchmark corpus, materialising a lone range was never
 * faster than that plan and much slower for wide ranges, because that plan stops early on a sorted sudo find and
 * probes only the readable things with authorization. A point and a range leg: decided in the statement, not by the
 * planner's estimates. A one-row {@code sel_pick} counts the point leg's index entries up to
 * {@value #POINT_COUNT_CAP} and the range leg's (both branches) up to that count + 1, and {@code sel_leg} materialises
 * the range leg only if it counted fewer (ties go to the point leg). The branch not chosen is skipped by a one-time
 * filter. At most one {@code sel_leg} per statement, one round trip.
 * </p>
 * <p>
 * <b>Estimate.</b> The pick's {@code sel_leg} returns its thing ids grouped
 * ({@code SELECT sli.thing_id FROM (…) sli GROUP BY sli.thing_id}). The semi-join reads {@code sel_leg} as a set, so
 * the result is the same. What changes is the row estimate the main statement is planned with: ungrouped, it is the
 * sum of the branches' estimates, and the multi-valued branch is estimated from the column-wide share of
 * {@code ord = 1} rows even on a path that holds no array. On the benchmark corpus that branch was estimated at
 * 31,795 rows and returned none, and the main statement then scanned every row of the equality instead of probing
 * the candidates. Grouped, the estimate is the planner's default group count. A range leg alone is never grouped:
 * with that small estimate a sorted sudo find would drive from every id instead of stopping early.
 * </p>
 */
final class SelectiveLegCte {

    /** The CTE with the chosen leg's thing ids (semi-joined by {@link SearchQueryAssembler}). */
    static final String LEG_CTE = "sel_leg";

    /** The one-row CTE of the runtime pick between a point and a range leg. */
    static final String PICK_CTE = "sel_pick";

    /** The point leg's index entries counted at most by the pick. */
    static final int POINT_COUNT_CAP = 50_000;

    /** The alias of the {@code search_flat} row the CTE bodies scan (distinct from the inline probe aliases). */
    private static final String CTE_FLAT_ALIAS = "sfc";
    private static final String ORD = "ord";
    private static final String POINT_WINS = "point_wins";
    private static final String PICK_ALIAS = "sp";
    private static final String POINT_COUNT_ALIAS = "pc";
    private static final String POINT_ROWS_ALIAS = "pr";
    private static final String RANGE_ROWS_ALIAS = "rr";
    private static final String GROUPED_ALIAS = "sli";
    private static final SqlExpression ONE = Sql.raw("1");
    private static final SqlExpression COUNT_STAR = Sql.raw("count(*)");
    private static final TimePlaceholder TIME_PLACEHOLDER = TimePlaceholder.getInstance();

    private SelectiveLegCte() {
        throw new AssertionError("no instances");
    }

    /**
     * @param criteria the (already thing-id-truncated) query criteria.
     * @param selectiveLegs which legs may be materialised.
     * @return the CTEs to prepend, or empty when no leg qualifies (⇒ plain {@code EXISTS} chain).
     */
    static Optional<Prelude> extract(final Criteria criteria, final SelectiveLegs selectiveLegs) {
        final Legs legs = criteria.accept(new Visitor());
        final Optional<PointLeg> point = legs.points().stream().findFirst();
        final Optional<RangeLeg> range =
                selectiveLegs == SelectiveLegs.POINT_AND_RANGE ? legs.firstRange() : Optional.empty();
        if (point.isPresent() && range.isPresent()) {
            return Optional.of(Prelude.picked(point.get(), range.get()));
        }
        // a range leg alone is never materialised: the statement keeps the plan it had before range legs
        return point.map(Prelude::point);
    }

    /**
     * The CTEs of one statement: the optional {@code sel_pick}, then {@code sel_leg}.
     */
    static final class Prelude {

        @Nullable private final Select pick;
        private final Select leg;

        private Prelude(@Nullable final Select pick, final Select leg) {
            this.pick = pick;
            this.leg = leg;
        }

        /**
         * Adds the CTEs to a statement; {@code sel_pick} first (CTEs render in call order).
         *
         * @param builder the statement.
         */
        void applyTo(final Select.Builder builder) {
            if (pick != null) {
                builder.withMaterialized(PICK_CTE, pick);
            }
            builder.withMaterialized(LEG_CTE, leg);
        }

        static Prelude point(final PointLeg point) {
            return new Prelude(null, flat(thingId(), pointRows(point)));
        }

        static Prelude picked(final PointLeg point, final RangeLeg range) {
            final Select pointCount = Sql.select().column(COUNT_STAR, "n")
                    .from(flatBuilder(ONE, pointRows(point))
                            .limit(Sql.raw(String.valueOf(POINT_COUNT_CAP)))
                            .build(), POINT_ROWS_ALIAS)
                    .build();
            final Select rangeCount = Sql.select().column(COUNT_STAR)
                    .from(flatBuilder(ONE, rangeRows(range))
                            .unionAll(flat(ONE, multiValuedRows(range)))
                            .limit(Sql.raw(POINT_COUNT_ALIAS + ".n + 1"))
                            .build(), RANGE_ROWS_ALIAS)
                    .build();
            final Select pick = Sql.select()
                    .column(Sql.le(Sql.col(POINT_COUNT_ALIAS, "n"), Sql.scalar(rangeCount)), POINT_WINS)
                    .from(pointCount, POINT_COUNT_ALIAS)
                    .build();
            final SqlExpression pointWins = Sql.scalar(Sql.select()
                    .column(Sql.col(PICK_ALIAS, POINT_WINS))
                    .from(PICK_CTE, PICK_ALIAS)
                    .build());
            final Select branches = flatBuilder(thingId(), Sql.and(pointRows(point), pointWins))
                    .unionAll(flat(thingId(), Sql.and(rangeRows(range), Sql.not(pointWins))))
                    .unionAll(flat(thingId(), Sql.and(multiValuedRows(range), Sql.not(pointWins))))
                    .build();
            return new Prelude(pick, groupedThingIds(branches));
        }
    }

    private static SqlExpression thingId() {
        return Sql.col(CTE_FLAT_ALIAS, AbstractFieldSqlCreator.THING_ID);
    }

    /**
     * {@code SELECT sli.thing_id FROM (<branches>) sli GROUP BY sli.thing_id}: the pick's thing ids, each once (see
     * "Estimate." in the class comment).
     */
    private static Select groupedThingIds(final Select branches) {
        final SqlExpression thingId = Sql.col(GROUPED_ALIAS, AbstractFieldSqlCreator.THING_ID);
        return Sql.select().column(thingId).from(branches, GROUPED_ALIAS).groupBy(thingId).build();
    }

    private static Select.Builder flatBuilder(final SqlExpression column, final SqlExpression where) {
        return Sql.select().column(column).from(AbstractFieldSqlCreator.FLAT_TABLE, CTE_FLAT_ALIAS).where(where);
    }

    private static Select flat(final SqlExpression column, final SqlExpression where) {
        return flatBuilder(column, where).build();
    }

    private static SqlExpression onPath(final String wpath) {
        return Sql.eq(Sql.col(CTE_FLAT_ALIAS, AbstractFieldSqlCreator.WPATH), Sql.text(wpath));
    }

    /** The point leg's body, byte for byte as before range legs: {@code (sfc.wpath = $ AND <value>)}. */
    private static SqlExpression pointRows(final PointLeg point) {
        return Sql.and(onPath(point.wpath()), point.predicate().flatCondition(CTE_FLAT_ALIAS));
    }

    /** One row meeting both bounds: each bound's own flat condition, as the translator renders it. */
    private static SqlExpression rangeRows(final RangeLeg range) {
        return Sql.and(onPath(range.wpath()), range.lower().flatCondition(CTE_FLAT_ALIAS),
                range.upper().flatCondition(CTE_FLAT_ALIAS));
    }

    /** Things with a second value at the path; {@code ord = 1} as a literal so sf_multi's predicate is provable. */
    private static SqlExpression multiValuedRows(final RangeLeg range) {
        return Sql.and(onPath(range.wpath()), Sql.eq(Sql.col(CTE_FLAT_ALIAS, ORD), ONE));
    }

    private record PointLeg(String wpath, SqlPredicate predicate) {}

    private record Bound(String wpath, boolean lower, SqlPredicate predicate) {}

    private record RangeLeg(String wpath, SqlPredicate lower, SqlPredicate upper) {}

    /** The candidate legs of the top-level conjuncts, in conjunct order. */
    private record Legs(List<PointLeg> points, List<Bound> bounds) {

        private static final Legs NONE = new Legs(List.of(), List.of());

        static Legs concat(final List<Legs> parts) {
            final List<PointLeg> points = new ArrayList<>();
            final List<Bound> bounds = new ArrayList<>();
            for (final Legs part : parts) {
                points.addAll(part.points());
                bounds.addAll(part.bounds());
            }
            return new Legs(List.copyOf(points), List.copyOf(bounds));
        }

        /** The first path (by its first bound) with a lower and an upper bound, using its first of each. */
        Optional<RangeLeg> firstRange() {
            final Map<String, SqlPredicate> lower = new LinkedHashMap<>();
            final Map<String, SqlPredicate> upper = new LinkedHashMap<>();
            final Set<String> paths = new LinkedHashSet<>();
            for (final Bound bound : bounds) {
                paths.add(bound.wpath());
                (bound.lower() ? lower : upper).putIfAbsent(bound.wpath(), bound.predicate());
            }
            return paths.stream()
                    .filter(wpath -> lower.containsKey(wpath) && upper.containsKey(wpath))
                    .findFirst()
                    .map(wpath -> new RangeLeg(wpath, lower.get(wpath), upper.get(wpath)));
        }
    }

    private enum LegKind {NONE, POINT, LOWER, UPPER}

    private static final class Visitor implements CriteriaVisitor<Legs> {

        @Override
        public Legs visitAny() {
            return Legs.NONE;
        }

        @Override
        public Legs visitExists(final ExistsFieldExpression fieldExpression) {
            return Legs.NONE;
        }

        @Override
        public Legs visitEmpty(final ExistsFieldExpression fieldExpression) {
            return Legs.NONE;
        }

        @Override
        public Legs visitField(final FilterFieldExpression fieldExpression, final Predicate predicate) {
            final LegKind kind = predicate.accept(LegKindProbe.INSTANCE);
            if (kind == LegKind.NONE) {
                return Legs.NONE;
            }
            final Optional<String> wpath = fieldExpression.acceptFilterVisitor(FlatWpathVisitor.INSTANCE);
            if (wpath.isEmpty()) {
                return Legs.NONE;
            }
            final SqlPredicate sqlPredicate = predicate.accept(new CreateSqlPredicateVisitor(
                    List.of(PlaceholderFactory.newPlaceholderResolver(TIME_PLACEHOLDER, new Object()))));
            // eq/in/ge/gt/le/lt never scope-negate (the probe already enforces that); a predicate that also matches
            // MISSING things (a placeholder resolved to null in eq, a null member the probe could not see because it
            // was a placeholder) or never matches is not a superset leg either — the resolved form is authoritative.
            if (sqlPredicate.scopedNegation() || sqlPredicate.matchesMissing() || sqlPredicate.neverMatches()) {
                return Legs.NONE;
            }
            return switch (kind) {
                case POINT -> new Legs(List.of(new PointLeg(wpath.get(), sqlPredicate)), List.of());
                case LOWER -> new Legs(List.of(), List.of(new Bound(wpath.get(), true, sqlPredicate)));
                case UPPER -> new Legs(List.of(), List.of(new Bound(wpath.get(), false, sqlPredicate)));
                case NONE -> Legs.NONE;
            };
        }

        @Override
        public Legs visitAnd(final List<Legs> conjuncts) {
            // a nested AND's conjuncts are top-level conjuncts as well (AND is associative)
            return Legs.concat(conjuncts);
        }

        @Override
        public Legs visitOr(final List<Legs> disjoints) {
            // a leg inside an OR is NOT a superset of the result set: never hoist it (correctness)
            return Legs.NONE;
        }

        @Override
        public Legs visitNor(final List<Legs> negativeDisjoints) {
            return Legs.NONE;
        }
    }

    /**
     * Point legs: eq(non-null)/in(non-empty, no null member). Bounds: ge/gt (lower) and le/lt (upper) with a numeric
     * value. A null operand (null = missing on MongoDB) is never a leg.
     */
    private static final class LegKindProbe implements PredicateVisitor<LegKind> {

        private static final LegKindProbe INSTANCE = new LegKindProbe();

        @Override
        public LegKind visitEq(@Nullable final Object value) {
            return value != null ? LegKind.POINT : LegKind.NONE;
        }

        @Override
        public LegKind visitIn(final List<?> values) {
            if (values.isEmpty()) {
                return LegKind.NONE;
            }
            for (final Object value : values) {
                if (value == null) {
                    return LegKind.NONE;
                }
            }
            return LegKind.POINT;
        }

        @Override
        public LegKind visitNe(@Nullable final Object value) {
            return LegKind.NONE;
        }

        @Override
        public LegKind visitGe(@Nullable final Object value) {
            return value instanceof Number ? LegKind.LOWER : LegKind.NONE;
        }

        @Override
        public LegKind visitGt(@Nullable final Object value) {
            return value instanceof Number ? LegKind.LOWER : LegKind.NONE;
        }

        @Override
        public LegKind visitLe(@Nullable final Object value) {
            return value instanceof Number ? LegKind.UPPER : LegKind.NONE;
        }

        @Override
        public LegKind visitLt(@Nullable final Object value) {
            return value instanceof Number ? LegKind.UPPER : LegKind.NONE;
        }

        @Override
        public LegKind visitLike(@Nullable final String value) {
            return LegKind.NONE;
        }

        @Override
        public LegKind visitILike(@Nullable final String value) {
            return LegKind.NONE;
        }

        @Override
        public LegKind visitLikeWithWildcards(@Nullable final String value) {
            return LegKind.NONE;
        }

        @Override
        public LegKind visitILikeWithWildcards(@Nullable final String value) {
            return LegKind.NONE;
        }
    }

    /**
     * Resolves a filter field expression to its flattened {@code wpath}, or empty for a field that has no single
     * selective flat leg — a root-mapped system column ({@code _id}/{@code _namespace}), or a wildcard feature path
     * ({@code features/*}), which spans a whole row family and is not a point lookup. Mirrors
     * {@link GetFilterSqlVisitor}'s pointer construction.
     */
    private static final class FlatWpathVisitor implements FilterFieldExpressionVisitor<Optional<String>> {

        private static final FlatWpathVisitor INSTANCE = new FlatWpathVisitor();
        private static final String FEATURE_ID_WILDCARD = "*";

        @Override
        public Optional<String> visitAttribute(final String key) {
            return Optional.of(AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/attributes/" + key)));
        }

        @Override
        public Optional<String> visitFeatureDefinition(final String featureId) {
            return isWildcard(featureId) ? Optional.empty()
                    : Optional.of(AbstractFieldSqlCreator.wpathOf(
                            JsonPointer.of("/features/" + featureId + "/definition")));
        }

        @Override
        public Optional<String> visitFeatureIdProperty(final String featureId, final String property) {
            return isWildcard(featureId) ? Optional.empty()
                    : Optional.of(AbstractFieldSqlCreator.wpathOf(
                            JsonPointer.of("/features/" + featureId + "/properties/" + property)));
        }

        @Override
        public Optional<String> visitFeatureIdDesiredProperty(final CharSequence featureId,
                final CharSequence desiredProperty) {
            return isWildcard(featureId) ? Optional.empty()
                    : Optional.of(AbstractFieldSqlCreator.wpathOf(
                            JsonPointer.of("/features/" + featureId + "/desiredProperties/" + desiredProperty)));
        }

        @Override
        public Optional<String> visitMetadata(final String key) {
            return Optional.of(AbstractFieldSqlCreator.wpathOf(JsonPointer.of("/_metadata/" + key)));
        }

        @Override
        public Optional<String> visitSimple(final String fieldName) {
            // Slash-mapped ⇒ a real flat leg; root-mapped (no leading slash) ⇒ a doc column, no selective flat leg.
            return fieldName.startsWith("/")
                    ? Optional.of(AbstractFieldSqlCreator.wpathOf(JsonPointer.of(fieldName)))
                    : Optional.empty();
        }

        private static boolean isWildcard(final CharSequence featureId) {
            return FEATURE_ID_WILDCARD.contentEquals(featureId);
        }
    }
}
