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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.rql.model.ParsedPlaceholder;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.criteria.Predicate;
import org.eclipse.ditto.rql.query.expression.FilterFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Golden-SQL unit tests for the candidate and preference rule of {@link SelectiveLegCte}: which conjunct becomes the
 * selective leg (point or range) with and without range legs, and the shape of the range leg (with its multi-valued
 * branch) only next to a point leg: a range alone renders the statement as before range legs, and the shape of the
 * runtime pick between a point and a range leg. Rendered through
 * {@link SearchQueryAssembler#count(Criteria, List, long, long, SelectiveLegs)} (sudo, plain count) so every assertion
 * is on real SQL. No database.
 */
public final class SelectiveLegCteTest {

    private static final SelectiveLegs POINT = SelectiveLegs.POINT;
    private static final SelectiveLegs RANGE = SelectiveLegs.POINT_AND_RANGE;

    private static final String SEMI_JOIN = "SELECT count(*) FROM search_things st WHERE (EXISTS (SELECT 1 FROM sel_leg "
            + "sl WHERE sl.thing_id = st.thing_id) AND ";

    private static final String POINT_ONLY = "WITH sel_leg AS MATERIALIZED (SELECT sfc.thing_id FROM search_flat sfc "
            + "WHERE (sfc.wpath = $1 AND sfc.val_text = $2)) " + SEMI_JOIN;

    /** A lone two-sided range, with or without range legs: the statement as before range legs (no CTE). */
    private static final String RANGE_ALONE = "SELECT count(*) FROM search_things st WHERE (EXISTS (SELECT 1 FROM "
            + "search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $1 AND sf.val_num >= $2)) AND EXISTS "
            + "(SELECT 1 FROM search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $3 AND "
            + "sf.val_num <= $4)))";

    private static final String PICKED = "WITH sel_pick AS MATERIALIZED (SELECT pc.n <= (SELECT count(*) FROM (SELECT 1 "
            + "FROM search_flat sfc WHERE (sfc.wpath = $1 AND sfc.val_num >= $2 AND sfc.val_num <= $3) UNION ALL "
            + "SELECT 1 FROM search_flat sfc WHERE (sfc.wpath = $4 AND sfc.ord = 1) LIMIT pc.n + 1) rr) AS point_wins "
            + "FROM (SELECT count(*) AS n FROM (SELECT 1 FROM search_flat sfc WHERE (sfc.wpath = $5 AND "
            + "sfc.val_text = $6) LIMIT 50000) pr) pc), "
            + "sel_leg AS MATERIALIZED (SELECT sli.thing_id FROM (SELECT sfc.thing_id FROM search_flat sfc WHERE "
            + "((sfc.wpath = $7 AND sfc.val_text = $8) AND (SELECT sp.point_wins FROM sel_pick sp)) UNION ALL SELECT "
            + "sfc.thing_id FROM search_flat sfc WHERE ((sfc.wpath = $9 AND sfc.val_num >= $10 AND sfc.val_num <= $11) "
            + "AND NOT (SELECT sp.point_wins FROM sel_pick sp)) UNION ALL SELECT sfc.thing_id FROM search_flat sfc "
            + "WHERE ((sfc.wpath = $12 AND sfc.ord = 1) AND NOT (SELECT sp.point_wins FROM sel_pick sp))) sli GROUP BY "
            + "sli.thing_id) " + SEMI_JOIN;

    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id", "namespace", "_namespace"));
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    @Test
    public void pointModeIsTheOldOverloadAndNeverRendersARangeLeg() {
        for (final Criteria criteria : List.of(
                eq("c", "x"), in("n", 1, 2), range("p", 5, 9), and(gt("p", 5), lt("p", 9)), range("p", 9, 5),
                and(eq("c", "x"), ge("p", 5), le("p", 9)), and(eq("c", "x"), range("p", 5, 9)),
                and(ge("a", 1), ge("b", 1), le("b", 2), ge("a", 5), le("a", 9)),
                and(eq("c", "x"), eq("d", "y"), ge("p", 5), le("p", 9)), ge("p", 5), and(eq("c", "x"), gt("p", 5)),
                and(ge("s", "a"), le("s", "z")), featureRange("*", 1, 2), featureRange("env", 1, 2),
                cf.or(List.of(range("p", 5, 9), eq("c", "x"))), cf.nor(range("p", 5, 9)),
                and(ge("a", 1), le("b", 2)), and(eq("c", null), ge("p", 5), le("p", 9)),
                and(field("c", cf.ne("x")), ge("p", 5), le("p", 9)), rootRange("a", "z"))) {
            final RenderedSql point = count(criteria, POINT);
            final RenderedSql today = SearchQueryAssembler.count(criteria, null, 0L, 0L);

            assertThat(point.sql()).as("%s", criteria).isEqualTo(today.sql())
                    .doesNotContain("UNION").doesNotContain("sel_pick");
            assertThat(binds(point)).isEqualTo(binds(today));
        }
    }

    @Test
    public void pointModeRendersTheStatementAsBeforeRangeLegs() {
        assertThat(count(in("n", 1, 2), POINT).sql()).isEqualTo("WITH sel_leg AS MATERIALIZED (SELECT sfc.thing_id "
                + "FROM search_flat sfc WHERE (sfc.wpath = $1 AND sfc.val_num = ANY($2::numeric[]))) SELECT count(*) "
                + "FROM search_things st WHERE (EXISTS (SELECT 1 FROM sel_leg sl WHERE sl.thing_id = st.thing_id) AND "
                + "EXISTS (SELECT 1 FROM search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $3 AND "
                + "sf.val_num = ANY($4::numeric[]))))");
        final RenderedSql eqAndRange = count(and(eq("c", "x"), ge("p", 5), le("p", 9)), POINT);
        assertThat(eqAndRange.sql()).isEqualTo(POINT_ONLY + "(EXISTS (SELECT 1 FROM search_flat sf WHERE "
                + "(sf.thing_id = st.thing_id AND sf.wpath = $3 AND sf.val_text = $4)) AND EXISTS (SELECT 1 FROM "
                + "search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $5 AND sf.val_num >= $6)) AND EXISTS "
                + "(SELECT 1 FROM search_flat sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $7 AND "
                + "sf.val_num <= $8))))");
        assertThat(eqAndRange.bindValues()).containsExactly("/attributes/c", "x", "/attributes/c", "x",
                "/attributes/p", BigDecimal.valueOf(5), "/attributes/p", BigDecimal.valueOf(9));
    }

    @Test
    public void eqAloneIsUnchangedWithRangeLegs() {
        assertSameInBothModes(eq("c", "x"));
        assertThat(count(eq("c", "x"), RANGE).sql()).startsWith(POINT_ONLY);
    }

    @Test
    public void inAloneIsUnchangedWithRangeLegs() {
        assertSameInBothModes(in("n", 1, 2));
    }

    @Test
    public void aTwoSidedRangeAloneKeepsTheStatementBeforeRangeLegs() {
        for (final Criteria lone : List.of(range("p", 5, 9), and(gt("p", 5), lt("p", 9)), range("p", 9, 5),
                featureRange("env", 1, 2), and(ge("a", 1), ge("b", 1), le("b", 2), ge("a", 5), le("a", 9)))) {
            assertSameInBothModes(lone);
            assertThat(count(lone, RANGE).sql()).as("%s", lone).doesNotContain("WITH ").doesNotContain("sel_");
        }
        assertThat(count(range("p", 5, 9), RANGE).sql()).isEqualTo(RANGE_ALONE);
        assertThat(count(range("p", 5, 9), RANGE).bindValues()).containsExactly(
                "/attributes/p", BigDecimal.valueOf(5), "/attributes/p", BigDecimal.valueOf(9));
    }

    /** A sorted sudo find with no CTE can stop early; a materialised or grouped range leg could not. */
    @Test
    public void aLoneRangeFindHasNoCteAndNoGrouping() {
        final PostgresSortClause sort = PostgresSortClause.of(List.of(
                new SortOption(fef.sortByThingId(), SortDirection.ASC)));
        final Criteria lone = range("p", 5, 9);
        final RenderedSql find = SearchQueryAssembler.findAll(lone, null, sort, 26L, 0L, RANGE);
        final RenderedSql unlimited = SearchQueryAssembler.findAllUnlimited(lone, null, sort, null, 0L, RANGE);

        assertThat(find.sql()).isEqualTo(SearchQueryAssembler.findAll(lone, null, sort, 26L, 0L, POINT).sql())
                .startsWith("SELECT st.thing_id, st.t_modified").doesNotContain("sel_leg").doesNotContain("GROUP BY");
        assertThat(binds(find)).isEqualTo(binds(SearchQueryAssembler.findAll(lone, null, sort, 26L, 0L, POINT)));
        assertThat(unlimited.sql()).isEqualTo(
                SearchQueryAssembler.findAllUnlimited(lone, null, sort, null, 0L, POINT).sql());
    }

    @Test
    public void gtAndLtFormARangeLegToo() {
        assertThat(count(and(eq("c", "x"), gt("p", 5), lt("p", 9)), RANGE).sql())
                .startsWith("WITH sel_pick AS MATERIALIZED (SELECT pc.n <= (SELECT count(*) FROM (SELECT 1 FROM "
                        + "search_flat sfc WHERE (sfc.wpath = $1 AND sfc.val_num > $2 AND sfc.val_num < $3) "
                        + "UNION ALL ");
    }

    @Test
    public void anEmptyIntervalIsStillARangeLeg() {
        // an array like [10, 1] matches and(ge(p,9),le(p,5)): each bound may be met by another element
        final RenderedSql r = count(and(eq("c", "x"), range("p", 9, 5)), RANGE);

        assertThat(r.sql()).startsWith(PICKED);
        assertThat(r.bindValues().subList(1, 3)).containsExactly(BigDecimal.valueOf(9), BigDecimal.valueOf(5));
    }

    @Test
    public void eqAndRangeGetTheRuntimePick() {
        final RenderedSql r = count(and(eq("c", "x"), ge("p", 5), le("p", 9)), RANGE);

        assertThat(r.sql()).startsWith(PICKED);
        assertThat(r.sql()).contains(") sli GROUP BY sli.thing_id) ");
        assertThat(r.sql().split("sel_leg AS MATERIALIZED", -1)).hasSize(2);
        assertThat(r.sql().indexOf("sel_pick AS MATERIALIZED")).isLessThan(r.sql().indexOf("sel_leg AS MATERIALIZED"));
        assertThat(r.sql()).contains("LIMIT 50000").contains("LIMIT pc.n + 1");
        assertThat(r.bindValues().subList(0, 12)).containsExactly(
                "/attributes/p", BigDecimal.valueOf(5), BigDecimal.valueOf(9), "/attributes/p",
                "/attributes/c", "x",
                "/attributes/c", "x",
                "/attributes/p", BigDecimal.valueOf(5), BigDecimal.valueOf(9),
                "/attributes/p");
    }

    @Test
    public void onlyThePicksLegIsGrouped() {
        assertThat(count(and(eq("c", "x"), ge("p", 5), le("p", 9)), RANGE).sql())
                .contains("), sel_leg AS MATERIALIZED (SELECT sli.thing_id FROM (SELECT sfc.thing_id FROM search_flat ")
                .contains(") sli GROUP BY sli.thing_id) SELECT count(*) FROM search_things st");
        for (final Criteria pointAlone : List.of(eq("c", "x"), in("n", 1, 2), and(eq("c", "x"), gt("p", 5)))) {
            assertThat(count(pointAlone, RANGE).sql()).as("%s", pointAlone).startsWith("WITH sel_leg AS MATERIALIZED "
                    + "(SELECT sfc.thing_id FROM search_flat sfc WHERE ").doesNotContain("GROUP BY");
        }
        assertThat(count(range("p", 5, 9), RANGE).sql()).doesNotContain("GROUP BY").doesNotContain("WITH ");
    }

    @Test
    public void thePairMayBeSplitAcrossNestedAnds() {
        assertThat(count(and(eq("c", "x"), range("p", 5, 9)), RANGE).sql()).startsWith(PICKED);
    }

    @Test
    public void theFirstPathWithBothBoundsWinsWithItsFirstBounds() {
        final RenderedSql r = count(and(eq("c", "x"), ge("a", 1), ge("b", 1), le("b", 2), ge("a", 5), le("a", 9)),
                RANGE);

        assertThat(r.sql()).startsWith(PICKED);
        assertThat(r.bindValues().subList(0, 4))
                .containsExactly("/attributes/a", BigDecimal.valueOf(1), BigDecimal.valueOf(9), "/attributes/a");
    }

    @Test
    public void onlyTheFirstPointLegCompetes() {
        final RenderedSql r = count(and(eq("c", "x"), eq("d", "y"), ge("p", 5), le("p", 9)), RANGE);

        assertThat(r.sql()).startsWith(PICKED);
        assertThat(r.bindValues().subList(4, 8)).containsExactly("/attributes/c", "x", "/attributes/c", "x");
    }

    @Test
    public void oneSidedRangesAreNotCandidates() {
        assertSameInBothModes(ge("p", 5));
        assertThat(count(ge("p", 5), RANGE).sql()).doesNotContain("WITH ");
        assertSameInBothModes(and(eq("c", "x"), gt("p", 5)));
        assertThat(count(and(eq("c", "x"), gt("p", 5)), RANGE).sql()).startsWith(POINT_ONLY);
    }

    @Test
    public void stringRangesAreNotCandidates() {
        assertSameInBothModes(and(ge("s", "a"), le("s", "z")));
    }

    @Test
    public void wildcardFeatureRangesAreNotCandidates() {
        assertSameInBothModes(featureRange("*", 1, 2));
    }

    @Test
    public void aNamedFeatureRangeIsACandidate() {
        final RenderedSql r = count(and(eq("c", "x"), featureRange("env", 1, 2)), RANGE);

        assertThat(r.sql()).startsWith(PICKED);
        assertThat(r.bindValues().get(0)).isEqualTo("/features/env/properties/t");
        assertThat(r.bindValues().get(3)).isEqualTo("/features/env/properties/t");
    }

    @Test
    public void rangesUnderOrOrNotAreNotCandidates() {
        assertSameInBothModes(cf.or(List.of(range("p", 5, 9), eq("c", "x"))));
        assertSameInBothModes(cf.nor(range("p", 5, 9)));
    }

    @Test
    public void boundsOnDifferentPathsAreNotAPair() {
        assertSameInBothModes(and(ge("a", 1), le("b", 2)));
    }

    @Test
    public void eqNullAndNeDoNotBecomePointLegsNextToARange() {
        for (final Criteria criteria : List.of(and(eq("c", null), ge("p", 5), le("p", 9)),
                and(field("c", cf.ne("x")), ge("p", 5), le("p", 9)))) {
            // no point leg ⇒ the range is alone ⇒ no CTE at all
            assertSameInBothModes(criteria);
            assertThat(count(criteria, RANGE).sql()).as("%s", criteria).doesNotContain("WITH ");
        }
    }

    @Test
    public void rootMappedFieldsAreNotCandidates() {
        assertSameInBothModes(rootRange("a", "z"));
        assertSameInBothModes(rootRange(1, 2));
    }

    @Test
    public void ordIsALiteralNotABind() {
        final RenderedSql r = count(and(eq("c", "x"), ge("p", 5), le("p", 9)), RANGE);

        assertThat(r.sql()).contains("sfc.ord = 1");
        assertThat(r.bindValues()).noneMatch(bind -> bind instanceof Integer || bind instanceof Long);
    }

    @Test
    public void aBoundedCountHasNoCteInEitherMode() {
        final Criteria criteria = and(eq("c", "x"), ge("p", 5), le("p", 9));
        for (final SelectiveLegs legs : SelectiveLegs.values()) {
            assertThat(SearchQueryAssembler.count(criteria, null, 0L, 10L, legs).sql()).as("%s", legs)
                    .doesNotContain("sel_leg").doesNotContain("sel_pick");
        }
    }

    @Test
    public void findAllAndFindAllUnlimitedGetTheSamePrelude() {
        final Criteria criteria = and(eq("c", "x"), ge("p", 5), le("p", 9));
        final PostgresSortClause sort = PostgresSortClause.of(List.of(
                new SortOption(fef.sortByThingId(), SortDirection.ASC)));
        final RenderedSql count = count(criteria, RANGE);
        final RenderedSql findAll = SearchQueryAssembler.findAll(criteria, null, sort, 26L, 0L, RANGE);
        final RenderedSql unlimited = SearchQueryAssembler.findAllUnlimited(criteria, null, sort, null, 0L, RANGE);
        final String prelude = count.sql().substring(0, count.sql().indexOf(SEMI_JOIN));

        assertThat(prelude).startsWith("WITH sel_pick AS MATERIALIZED").contains("), sel_leg AS MATERIALIZED (");
        assertThat(findAll.sql()).startsWith(prelude + "SELECT st.thing_id, st.t_modified");
        assertThat(unlimited.sql()).startsWith(prelude + "SELECT st.thing_id FROM search_things st");
        assertThat(findAll.bindValues().subList(0, 12)).isEqualTo(count.bindValues().subList(0, 12));
        assertThat(unlimited.bindValues().subList(0, 12)).isEqualTo(count.bindValues().subList(0, 12));
    }

    @Test
    public void anInWithANullMemberIsNotAPointLeg() {
        // in(p, "x", null) also matches things with NO row at p (Mongo null = missing); a leg materialised from flat
        // rows can never produce those, so hoisting it would drop them (the CTE must stay a superset pre-filter).
        for (final SelectiveLegs legs : List.of(POINT, RANGE)) {
            assertThat(count(inNullable("c", "x", null), legs).sql()).as("%s", legs)
                    .doesNotContain("WITH ").doesNotContain("sel_");
            assertThat(count(inNullable("c", (Object) null), legs).sql()).as("%s", legs)
                    .doesNotContain("WITH ").doesNotContain("sel_");
            // the next point conjunct takes over
            final RenderedSql next = count(and(inNullable("c", "x", null), eq("d", "y")), legs);
            assertThat(next.sql()).as("%s", legs).startsWith(POINT_ONLY);
            assertThat(next.bindValues().subList(0, 2)).as("%s", legs).containsExactly("/attributes/d", "y");
            // without the null member the same in() is a point leg as before
            assertThat(count(inNullable("c", "x"), legs).sql()).as("%s", legs)
                    .startsWith("WITH sel_leg AS MATERIALIZED (SELECT sfc.thing_id FROM search_flat sfc WHERE "
                            + "(sfc.wpath = $1 AND sfc.val_text = ANY($2::text[]))) ");
        }
    }

    @Test
    public void aPlaceholderThatResolvesToNullIsNotAPointLeg() {
        // eq(p, <unresolvable placeholder>) is Mongo's bare {p: null} (null OR missing) — not a point lookup either.
        final Object unresolvable = ParsedPlaceholder.of("time:bogus");
        for (final SelectiveLegs legs : List.of(POINT, RANGE)) {
            assertThat(count(eq("c", unresolvable), legs).sql()).as("%s", legs)
                    .doesNotContain("WITH ").doesNotContain("sel_");
            assertThat(count(and(eq("c", unresolvable), eq("d", "y")), legs).sql()).as("%s", legs)
                    .startsWith(POINT_ONLY);
            assertThat(count(and(inNullable("c", "x", unresolvable), eq("d", "y")), legs).sql()).as("%s", legs)
                    .startsWith(POINT_ONLY);
        }
    }

    @Test
    public void gtAndLtNullAreNeverLegsAndRenderFalse() {
        for (final SelectiveLegs legs : List.of(POINT, RANGE)) {
            final RenderedSql r = count(and(gt("p", null), le("p", 9), eq("c", "x")), legs);
            assertThat(r.sql()).as("%s", legs).startsWith(POINT_ONLY).contains("(false AND EXISTS");
            assertThat(r.sql()).as("%s", legs).doesNotContain("sel_pick");
        }
    }

    private void assertSameInBothModes(final Criteria criteria) {
        final RenderedSql point = count(criteria, POINT);
        final RenderedSql range = count(criteria, RANGE);

        assertThat(range.sql()).as("%s", criteria).isEqualTo(point.sql());
        assertThat(binds(range)).as("%s", criteria).isEqualTo(binds(point));
    }

    /** The bind values with array binds (e.g. of {@code in}) as lists, so that they compare by content. */
    private static List<Object> binds(final RenderedSql sql) {
        return sql.bindValues().stream()
                .map(value -> value instanceof Object[] array ? Arrays.asList(array) : value)
                .toList();
    }

    private static RenderedSql count(final Criteria criteria, final SelectiveLegs legs) {
        return SearchQueryAssembler.count(criteria, null, 0L, 0L, legs);
    }

    private FilterFieldExpression attr(final String name) {
        return fef.filterByAttribute(name);
    }

    private Criteria field(final String attribute, final Predicate predicate) {
        return cf.fieldCriteria(attr(attribute), predicate);
    }

    private Criteria eq(final String attribute, final Object value) {
        return field(attribute, cf.eq(value));
    }

    private Criteria in(final String attribute, final Object... values) {
        return field(attribute, cf.in(List.of(values)));
    }

    /** {@code in} with values that may include {@code null} ({@code List.of} rejects null elements). */
    private Criteria inNullable(final String attribute, final Object... values) {
        return field(attribute, cf.in(Arrays.asList(values)));
    }

    private Criteria ge(final String attribute, final Object value) {
        return field(attribute, cf.ge(value));
    }

    private Criteria gt(final String attribute, final Object value) {
        return field(attribute, cf.gt(value));
    }

    private Criteria le(final String attribute, final Object value) {
        return field(attribute, cf.le(value));
    }

    private Criteria lt(final String attribute, final Object value) {
        return field(attribute, cf.lt(value));
    }

    private Criteria range(final String attribute, final Object lo, final Object hi) {
        return and(ge(attribute, lo), le(attribute, hi));
    }

    private Criteria featureRange(final String featureId, final Object lo, final Object hi) {
        final FilterFieldExpression t = fef.filterByFeatureProperty(featureId, "t");
        return and(cf.fieldCriteria(t, cf.ge(lo)), cf.fieldCriteria(t, cf.le(hi)));
    }

    private Criteria rootRange(final Object lo, final Object hi) {
        return and(cf.fieldCriteria(fef.filterByThingId(), cf.ge(lo)), cf.fieldCriteria(fef.filterByThingId(),
                cf.le(hi)));
    }

    private Criteria and(final Criteria... conjuncts) {
        return cf.and(List.of(conjuncts));
    }

}
