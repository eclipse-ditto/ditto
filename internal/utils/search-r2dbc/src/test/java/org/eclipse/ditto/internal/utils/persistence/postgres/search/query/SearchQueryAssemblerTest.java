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

import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;

/**
 * Golden-SQL unit tests for {@link SearchQueryAssembler} (Task D4 statement assembly) — the {@code findAll}/{@code
 * count} shapes and, in particular, BOTH branches of the §3.5 selective-leg rescue CTE heuristic (present when a
 * top-level {@code eq}/{@code in} conjunct exists; absent — plain EXISTS chain — otherwise). No database.
 */
public final class SearchQueryAssemblerTest {

    private static final List<String> SUBJECTS = List.of("s1", "s2");

    private static final Map<String, String> MAPPINGS = Map.of(
            "thingId", "_id", "namespace", "_namespace");

    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(MAPPINGS);
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    private PostgresSortClause thingIdSort() {
        return PostgresSortClause.of(List.of(asc(fef.sortByThingId())));
    }

    // ============================================================================= selective-leg CTE — both branches

    @Test
    public void eqLegStagesMaterializedSelectiveCte() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);

        assertThat(r.sql()).startsWith("WITH sel_leg AS MATERIALIZED (SELECT sfc.thing_id FROM search_flat sfc WHERE "
                + "(sfc.wpath = $1 AND sfc.val_text = $2))");
        // the CTE is joined by an additional semi-join conjunct; the full predicate (incl. auth) is still applied.
        assertThat(r.sql()).contains(
                "EXISTS (SELECT 1 FROM sel_leg sl WHERE sl.thing_id = st.thing_id)");
        assertThat(r.sql()).contains("st.global_read && "); // auth still present
        assertThat(r.bindValues()).contains("/attributes/color", "red");
    }

    @Test
    public void inLegStagesMaterializedSelectiveCte() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("n"), cf.in(List.of(1, 2, 3)));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).contains("WITH sel_leg AS MATERIALIZED");
        assertThat(r.sql()).contains("sfc.val_num = ANY(");
    }

    @Test
    public void firstEqConjunctOfTopLevelAndBecomesCte() {
        final Criteria criteria = cf.and(List.of(
                cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5)),  // not selective
                cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red")))); // selective -> the CTE
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).contains("WITH sel_leg AS MATERIALIZED");
        assertThat(r.bindValues()).contains("/attributes/color", "red");
    }

    @Test
    public void rangeOnlyCriterionHasNoCteWithPointLegsOnly() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).doesNotContain("WITH ").doesNotContain("sel_leg");
        assertThat(r.sql()).startsWith("SELECT st.thing_id, st.t_modified");
    }

    @Test
    public void aTwoSidedRangeAloneHasNoCteEvenWithRangeLegs() {
        // a lone one-sided range has none either
        final Criteria oneSided = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        assertThat(SearchQueryAssembler.findAll(oneSided, SUBJECTS, thingIdSort(), 26L, 0L,
                SelectiveLegs.POINT_AND_RANGE).sql()).doesNotContain("sel_leg");

        final Criteria twoSided = cf.and(List.of(
                cf.fieldCriteria(fef.filterByAttribute("range"), cf.ge(5)),
                cf.fieldCriteria(fef.filterByAttribute("range"), cf.le(9))));
        final RenderedSql r = SearchQueryAssembler.findAll(twoSided, SUBJECTS, thingIdSort(), 26L, 0L,
                SelectiveLegs.POINT_AND_RANGE);
        assertThat(r.sql()).isEqualTo(SearchQueryAssembler.findAll(twoSided, SUBJECTS, thingIdSort(), 26L, 0L).sql())
                .startsWith("SELECT st.thing_id, st.t_modified").doesNotContain("sel_leg")
                .doesNotContain("GROUP BY").contains("st.global_read && ");
    }

    @Test
    public void eqAndRangeReadTheGroupedLegWithRangeLegs() {
        final Criteria pair = cf.and(List.of(
                cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red")),
                cf.fieldCriteria(fef.filterByAttribute("range"), cf.ge(5)),
                cf.fieldCriteria(fef.filterByAttribute("range"), cf.le(9))));
        final RenderedSql r = SearchQueryAssembler.findAll(pair, SUBJECTS, thingIdSort(), 26L, 0L,
                SelectiveLegs.POINT_AND_RANGE);
        assertThat(r.sql()).startsWith("WITH sel_pick AS MATERIALIZED (")
                .contains("), sel_leg AS MATERIALIZED (SELECT sli.thing_id FROM (SELECT sfc.thing_id FROM "
                        + "search_flat sfc WHERE ")
                .contains(") sli GROUP BY sli.thing_id) SELECT st.thing_id, st.t_modified")
                .contains("EXISTS (SELECT 1 FROM sel_leg sl WHERE sl.thing_id = st.thing_id)")
                .contains("st.global_read && ");
    }

    @Test
    public void eqInsideOrIsNotHoistedIntoCte() {
        // Correctness: a leg inside an OR is not a superset of the result set — never hoist it.
        final Criteria criteria = cf.or(List.of(
                cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red")),
                cf.fieldCriteria(fef.filterByAttribute("shape"), cf.eq("round"))));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).doesNotContain("sel_leg");
    }

    @Test
    public void eqNullIsNotSelective() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("x"), cf.eq(null));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).doesNotContain("sel_leg");
    }

    @Test
    public void rootMappedEqIsNotAFlatSelectiveLeg() {
        // thingId eq -> a doc column, not a flat leg -> no CTE.
        final Criteria criteria = cf.fieldCriteria(fef.filterByThingId(), cf.eq("ns:thing1"));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).doesNotContain("sel_leg");
    }

    // ============================================================================= projection / paging / sudo-vs-auth

    @Test
    public void findAllProjectsThingIdModifiedAndPagesWithLimitOffset() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, SUBJECTS, thingIdSort(), 26L, 50L);
        assertThat(r.sql()).startsWith("SELECT st.thing_id, st.t_modified");
        assertThat(r.sql()).contains(" LIMIT ").contains(" OFFSET ");
        assertThat(r.bindValues()).contains(26L, 50L);
    }

    @Test
    public void sudoFindAllOmitsGlobalReadAuth() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        final RenderedSql r = SearchQueryAssembler.findAll(criteria, null, thingIdSort(), 26L, 0L);
        assertThat(r.sql()).doesNotContain("global_read").doesNotContain("policy_auth");
    }

    @Test
    public void findAllUnlimitedHasNoCursorProjectionAndOptionalLimit() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        final RenderedSql bounded = SearchQueryAssembler.findAllUnlimited(criteria, SUBJECTS, thingIdSort(), 100L, 0L);
        assertThat(bounded.sql()).startsWith("SELECT st.thing_id FROM search_things st");
        assertThat(bounded.sql()).doesNotContain("s0_"); // no cursor-projection columns
        assertThat(bounded.sql()).contains(" LIMIT ");

        final RenderedSql unbounded = SearchQueryAssembler.findAllUnlimited(criteria, SUBJECTS, thingIdSort(), null, 0L);
        assertThat(unbounded.sql()).doesNotContain(" LIMIT ").doesNotContain(" OFFSET ");
    }

    // ============================================================================= count

    @Test
    public void plainCountIsCountStarWithSelectiveCte() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        final RenderedSql r = SearchQueryAssembler.count(criteria, SUBJECTS, 0, 0);
        assertThat(r.sql()).startsWith("WITH sel_leg AS MATERIALIZED");
        assertThat(r.sql()).contains("SELECT count(*) FROM search_things st WHERE");
    }

    @Test
    public void sudoCountOmitsAuth() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        final RenderedSql r = SearchQueryAssembler.count(criteria, null, 0, 0);
        assertThat(r.sql()).isEqualTo("SELECT count(*) FROM search_things st WHERE EXISTS (SELECT 1 FROM search_flat "
                + "sf WHERE (sf.thing_id = st.thing_id AND sf.wpath = $1 AND sf.val_num > $2))");
    }

    @Test
    public void countWithSkipAndLimitWrapsInBoundedCte() {
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("range"), cf.gt(5));
        final RenderedSql r = SearchQueryAssembler.count(criteria, SUBJECTS, 10, 25);
        assertThat(r.sql()).startsWith("WITH matched AS (SELECT 1 FROM search_things st WHERE");
        assertThat(r.sql()).contains(" LIMIT ").contains(" OFFSET ");
        assertThat(r.sql()).endsWith("SELECT count(*) FROM matched");
        assertThat(r.bindValues()).contains(25L, 10L);
    }

    @Test
    public void sortLateralCarriesNoAuthorization() {
        // Sort keys are never authorization-filtered (plan §3.3 exception 3, mirrored in PostgresSortClause's javadoc;
        // Mongo sorts the unfiltered thing too): the subjects restrict WHICH things match, never their sort key.
        final PostgresSortClause sort = PostgresSortClause.of(
                List.of(asc(fef.sortByFeatureProperty("env", "temperature")), asc(fef.sortByThingId())));
        final String sql = SearchQueryAssembler.findAll(cf.any(), SUBJECTS, sort, 26L, 0L).sql();
        final String lateral = sql.substring(sql.indexOf("LEFT JOIN LATERAL"), sql.indexOf(") k0 ON true"));
        assertThat(lateral).doesNotContain("global_read").doesNotContain("policy_auth").doesNotContain("features_auth");
        assertThat(sql).contains("st.global_read && "); // visibility is still restricted, outside the lateral
    }

    @Test
    public void namespaceReportGroupsByNamespace() {
        assertThat(SearchQueryAssembler.Fixed.NAMESPACE_REPORT_SQL)
                .isEqualTo("SELECT namespace, count(*) AS count FROM search_things GROUP BY namespace");
    }

    private static SortOption asc(final SortFieldExpression expression) {
        return new SortOption(expression, SortDirection.ASC);
    }
}
