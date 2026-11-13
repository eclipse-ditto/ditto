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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Select;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.Sql;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.SortFieldExpression;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Golden-SQL unit tests for the Task-D3 sort lateral + {@code ORDER BY} assembly + cursor value extraction + resume
 * criteria translation. No database: exact rendered SQL + ordered binds via the D1 renderer, and JSON cursor values via
 * a map-backed {@link SortValueAccessor}.
 * <p>
 * The four §3.5 sort-parity requirements are each pinned by a named test:
 * <ul>
 *   <li><b>(a) missing == null</b> → {@link #missingKeyCoalescesIntoNullRankTyingWithExplicitNull()} +
 *       {@link #resumeNullSortValueTranslatesMissingOrExplicitNull()}</li>
 *   <li><b>(b) row-wise min/max element (no chimera aggregates)</b> → {@link #lateralPicksBoundaryElementRowWiseAsc()}
 *       + {@link #lateralPicksBoundaryElementRowWiseDesc()}</li>
 *   <li><b>(c) val_bool in the ordering tuple (BSON false &lt; true)</b> → {@link #orderByTupleIncludesValBool()}</li>
 *   <li><b>(d) empty array maps to the null rank</b> → {@link #orderByCaseFoldsEmptyArrayRankIntoNullRank()} +
 *       {@link #sortValueOfEmptyArrayBoundaryIsNull()}</li>
 * </ul>
 */
public final class PostgresSortAndCursorTest {

    /** The service's default simple-field-mappings (search.conf) — same set the D2 translation test uses. */
    private static final Map<String, String> MAPPINGS = Map.of(
            "thingId", "_id",
            "namespace", "_namespace",
            "policyId", "/policyId",
            "_revision", "/_revision",
            "_modified", "/_modified",
            "_created", "/_created",
            "definition", "/definition");

    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(MAPPINGS);
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    // ============================================================================ (b) sort lateral: row-wise boundary

    @Test
    public void lateralPicksBoundaryElementRowWiseAsc() {
        // ASC sort key: the lateral selects the BSON-minimum element as ONE row (ORDER BY … LIMIT 1), NOT per-column
        // min()/max() aggregates (the chimera-tuple hazard). The inner ORDER BY is bare ASC on all four columns.
        final RenderedSql r = renderSort(asc(fef.sortByAttribute("temp")));
        assertThat(r.sql()).isEqualTo(
                "SELECT st.thing_id FROM search_things st "
                        + "LEFT JOIN LATERAL (SELECT s.type_rank, s.val_num, s.val_text, s.val_bool "
                        + "FROM search_flat s WHERE (s.thing_id = st.thing_id AND s.path = $1 AND s.wpath = $2) "
                        + "ORDER BY s.type_rank, s.val_num, s.val_text, s.val_bool LIMIT 1) k0 ON true "
                        + "ORDER BY COALESCE(CASE WHEN k0.type_rank = 5 THEN 1 ELSE k0.type_rank END, 1), "
                        + "k0.val_num, k0.val_text, k0.val_bool");
        assertThat(r.bindValues()).containsExactly("/attributes/temp", "/attributes/temp");
        // No aggregate functions anywhere — the boundary is a whole row.
        assertThat(r.sql()).doesNotContain("min(").doesNotContain("max(").doesNotContain("MIN(").doesNotContain("MAX(");
    }

    // ============================================================================ lateral probe: full primary-key prefix

    @Test
    public void lateralBindsPathToTheSortWpathSoTheProbeUsesTheWholePrimaryKeyPrefix() {
        // search_flat's primary key is (thing_id, path, wpath, ord). A sort wpath is never wildcarded (rejected below),
        // and every non-wildcard row has wpath = path (ThingFlattener emits the exact row with wpath = path; the
        // /features/* twin's wpath is the wildcarded one). So "s.path = <wpath>" is redundant for the result but
        // bounds the probe on (thing_id, path, wpath) instead of scanning every row of the thing. Both binds
        // carry the same concrete wpath — for a feature property too, never the /features/* form.
        final RenderedSql r = renderSort(asc(fef.sortByFeatureProperty("env", "temperature")));
        assertThat(r.sql()).contains(
                "FROM search_flat s WHERE (s.thing_id = st.thing_id AND s.path = $1 AND s.wpath = $2) ");
        assertThat(r.bindValues()).containsExactly("/features/env/properties/temperature",
                "/features/env/properties/temperature");
    }

    @Test
    public void lateralPicksBoundaryElementRowWiseDesc() {
        // DESC applies to the WHOLE key: the inner boundary selection flips to max-first (all columns DESC) AND every
        // outer ORDER BY component is DESC (Mongo whole-key direction semantics).
        final RenderedSql r = renderSort(desc(fef.sortByAttribute("temp")));
        assertThat(r.sql()).contains(
                "ORDER BY s.type_rank DESC, s.val_num DESC, s.val_text DESC, s.val_bool DESC LIMIT 1) k0 ON true");
        assertThat(r.sql()).endsWith(
                "ORDER BY COALESCE(CASE WHEN k0.type_rank = 5 THEN 1 ELSE k0.type_rank END, 1) DESC, "
                        + "k0.val_num DESC, k0.val_text DESC, k0.val_bool DESC");
    }

    // ============================================================================ (c) val_bool part of ordering tuple

    @Test
    public void orderByTupleIncludesValBool() {
        // Without val_bool, rank-6 rows (NULL num/text) would fall to the thing-id tiebreak for false/true ordering.
        // Postgres false < true == BSON false < true, so val_bool must be a tuple component.
        final RenderedSql r = renderSort(asc(fef.sortByAttribute("flag")));
        assertThat(r.sql()).contains("k0.val_num, k0.val_text, k0.val_bool");
        // and it is also the last inner boundary-selection column.
        assertThat(r.sql()).contains("ORDER BY s.type_rank, s.val_num, s.val_text, s.val_bool LIMIT 1");
    }

    // ============================================================================ (a)/(d) rank coalescing + empty CASE

    @Test
    public void missingKeyCoalescesIntoNullRankTyingWithExplicitNull() {
        // (a): a LEFT JOIN keeps things whose key is missing (all lateral columns NULL); COALESCE(…, 1) folds that into
        // the null rank so missing ties with an explicit null — NOT plain NULLS FIRST (which would order missing first).
        final RenderedSql r = renderSort(asc(fef.sortByAttribute("x")));
        assertThat(r.sql()).contains("LEFT JOIN LATERAL");
        assertThat(r.sql()).contains("COALESCE(CASE WHEN k0.type_rank = 5 THEN 1 ELSE k0.type_rank END, 1)");
        assertThat(r.sql()).doesNotContain("NULLS FIRST").doesNotContain("NULLS LAST");
    }

    @Test
    public void orderByCaseFoldsEmptyArrayRankIntoNullRank() {
        // (d): an empty array flattens to a single valueless type_rank=5 (TYPE_ARRAY) stub; the CASE folds 5 → 1 so it
        // ties with null/missing in the sort.
        final RenderedSql r = renderSort(asc(fef.sortByAttribute("x")));
        assertThat(r.sql()).contains("CASE WHEN k0.type_rank = 5 THEN 1 ELSE k0.type_rank END");
    }

    // ============================================================================ ORDER BY assembly (1 & 2 keys, tiebreak)

    @Test
    public void twoSortKeysWithTrailingThingIdTiebreak() {
        // Post-truncation shape [attr a ASC, _id ASC]: ONE lateral (the flat key) + the root thing-id key contributes a
        // bare st.thing_id ORDER BY component with no lateral — the total-order tiebreak, present via the truncation rule.
        final RenderedSql r = renderSort(asc(fef.sortByAttribute("a")), asc(fef.sortByThingId()));
        assertThat(r.sql()).containsOnlyOnce("LEFT JOIN LATERAL");
        assertThat(r.sql()).endsWith(
                "ORDER BY COALESCE(CASE WHEN k0.type_rank = 5 THEN 1 ELSE k0.type_rank END, 1), "
                        + "k0.val_num, k0.val_text, k0.val_bool, st.thing_id");
    }

    @Test
    public void rootMappedThingIdSortIsBareColumnNoLateral() {
        // _id → st.thing_id: NOT-NULL scalar column, sorted directly (no lateral, no COALESCE/CASE, no rank tuple).
        final RenderedSql r = renderSort(asc(fef.sortByThingId()));
        assertThat(r.sql()).isEqualTo("SELECT st.thing_id FROM search_things st ORDER BY st.thing_id");
        assertThat(r.bindValues()).isEmpty();
    }

    @Test
    public void rootMappedNamespaceSortIsBareColumn() {
        final RenderedSql r = renderSort(desc(fef.sortBy("namespace")));
        assertThat(r.sql()).isEqualTo("SELECT st.thing_id FROM search_things st ORDER BY st.namespace DESC");
    }

    @Test
    public void slashMappedSortIsFlatLateral() {
        // _modified → /_modified: slash-mapped ⇒ flat lateral (unlike the root-mapped system columns).
        final RenderedSql r = renderSort(asc(fef.sortBy("_modified")));
        assertThat(r.sql()).contains("LEFT JOIN LATERAL");
        assertThat(r.bindValues()).containsExactly("/_modified", "/_modified");
    }

    @Test
    public void featurePropertySortResolvesConcreteWpath() {
        final RenderedSql r = renderSort(asc(fef.sortByFeatureProperty("f1", "temp")));
        assertThat(r.bindValues()).containsExactly("/features/f1/properties/temp", "/features/f1/properties/temp");
    }

    // ============================================================================ wildcard sort rejection (parity)

    @Test
    public void wildcardFeatureSortIsRejected() {
        // Parity with Mongo (no wildcard sort branch): reject rather than silently sort across features via the
        // /features/* flat rows — same IllegalArgumentException family the field-expression factory raises.
        assertThatThrownBy(() -> PostgresSortClause.of(List.of(asc(fef.sortByFeatureProperty("*", "x")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wildcard");
        assertThatThrownBy(() -> PostgresSortClause.of(List.of(asc(fef.sortByFeatureDesiredProperty("*", "x")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void wildcardWpathReachingTheLateralFailsLoudly() {
        // GetSortSqlVisitor rejects a "*" feature id, but a configured simple-field mapping can still name a
        // /features/* wpath directly. The lateral binds path = wpath, which no /features/* row satisfies, so such a
        // key would silently sort every thing as missing: it must fail instead.
        final ThingsFieldExpressionFactory wildcardMapping =
                ThingsFieldExpressionFactory.of(Map.of("wildTemp", "/features/*/properties/temp"));
        final PostgresSortClause clause = PostgresSortClause.of(List.of(asc(wildcardMapping.sortBy("wildTemp"))));
        assertThatThrownBy(() -> clause.applyOrdering(baseSelect()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/features/*/properties/temp");
    }

    // ============================================================================ cursor projection (handoff to D4)

    @Test
    public void cursorProjectionsAliasFlatAndRootColumns() {
        final Select.Builder builder = baseSelect();
        final PostgresSortClause clause = PostgresSortClause.of(List.of(asc(fef.sortByAttribute("a")),
                asc(fef.sortByThingId())));
        clause.applyOrdering(builder);
        clause.applyCursorProjections(builder);
        final String sql = Sql.render(builder.build()).sql();
        assertThat(sql).contains("k0.type_rank AS s0_rank");
        assertThat(sql).contains("k0.val_num AS s0_num");
        assertThat(sql).contains("k0.val_text AS s0_text");
        assertThat(sql).contains("k0.val_bool AS s0_bool");
        assertThat(sql).contains("st.thing_id AS s1_val");
    }

    // ============================================================================ sortValues extraction matrix

    @Test
    public void sortValueOfNumberBoundaryPreservesIntegral() {
        assertThat(flatSortValue(2, new BigDecimal("42"), null, null)).isEqualTo("[42]");
    }

    @Test
    public void sortValueOfNumberBoundaryPreservesDouble() {
        assertThat(flatSortValue(2, new BigDecimal("1.5"), null, null)).isEqualTo("[1.5]");
    }

    @Test
    public void sortValueOfStringBoundary() {
        assertThat(flatSortValue(3, null, "hi", null)).isEqualTo("[\"hi\"]");
    }

    @Test
    public void sortValueOfBooleanBoundary() {
        assertThat(flatSortValue(6, null, null, Boolean.TRUE)).isEqualTo("[true]");
        assertThat(flatSortValue(6, null, null, Boolean.FALSE)).isEqualTo("[false]");
    }

    @Test
    public void sortValueOfNullRankBoundaryIsNull() {
        assertThat(flatSortValue(1, null, null, null)).isEqualTo("[null]");
    }

    @Test
    public void sortValueOfMissingKeyIsNull() {
        // typeRank column NULL (LEFT JOIN found no row) → null cursor value (ties with explicit null on resume).
        assertThat(flatSortValue(null, null, null, null)).isEqualTo("[null]");
    }

    @Test
    public void sortValueOfEmptyArrayBoundaryIsNull() {
        // (d): rank-5 (empty array / object) boundary has no scalar cursor value → null (documented §3.5 divergence).
        assertThat(flatSortValue(5, null, null, null)).isEqualTo("[null]");
        assertThat(flatSortValue(4, null, null, null)).isEqualTo("[null]"); // object boundary likewise
    }

    @Test
    public void sortValueOfRootColumnIsTextValue() {
        final PostgresSortClause clause = PostgresSortClause.of(List.of(asc(fef.sortByThingId())));
        final Map<String, Object> row = new HashMap<>();
        row.put("s0_val", "ns:thing1");
        assertThat(clause.toSortValues(accessor(row)).toString()).isEqualTo("[\"ns:thing1\"]");
    }

    // ============================================================================ resume-criteria translation (D2 reuse)

    @Test
    public void resumeNullSortValueTranslatesMissingOrExplicitNull() {
        // getNextDimensionCriteria's null branch: (NOR exists) OR (field == null). Must translate to (NOT EXISTS(wpath)
        // OR EXISTS(wpath ∧ type_rank = TYPE_NULL)) — i.e. capture BOTH missing AND explicit-null things.
        final SortFieldExpression sort = fef.sortByAttribute("x");
        final Criteria resume = cf.or(List.of(
                cf.nor(cf.existsCriteria(sort)),
                cf.fieldCriteria(sort, cf.eq(null))));
        final RenderedSql r = sudo(resume);
        assertThat(r.sql()).contains("NOT EXISTS (SELECT 1 FROM search_flat sf WHERE "
                + "(sf.thing_id = st.thing_id AND sf.wpath = $1))");
        assertThat(r.sql()).contains("sf.type_rank = $3");
        assertThat(r.bindValues()).containsExactly("/attributes/x", "/attributes/x", 1); // 1 == TYPE_NULL
    }

    @Test
    public void resumeStringSortValueLtTranslatesWithNullSortsBefore() {
        // getDimensionLtCriteria ASC-nonnull: (field < value) OR (NOR exists) — value-precedes OR null-precedes.
        final SortFieldExpression sort = fef.sortByAttribute("x");
        final Criteria resume = cf.or(List.of(
                cf.fieldCriteria(sort, cf.lt("m")),
                cf.nor(cf.existsCriteria(sort))));
        final RenderedSql r = sudo(resume);
        assertThat(r.sql()).contains("sf.val_text < $2");
        assertThat(r.sql()).contains("NOT EXISTS");
        assertThat(r.bindValues()).contains("m");
    }

    @Test
    public void resumeTypedEqAndBoundaryCrossingTranslate() {
        final SortFieldExpression sort = fef.sortByAttribute("x");
        // typed eq (nonnull thisDimensionEq) → val_text =.
        assertThat(sudo(cf.fieldCriteria(sort, cf.eq("m"))).sql()).contains("sf.val_text = $2");
        // crossing into a numeric boundary → val_num <.
        assertThat(sudo(cf.fieldCriteria(sort, cf.lt(5))).sql()).contains("sf.val_num < $2");
    }

    // ============================================================================ thing-id tiebreak guard (Finding 2)

    @Test
    public void thingIdTerminatedSortEmitsNoTiebreakWarning() {
        final ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            PostgresSortClause.of(List.of(asc(fef.sortByAttribute("a")), asc(fef.sortByThingId())));
            assertThat(appender.list).isEmpty();
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    public void sortNotTerminatedByThingIdEmitsTiebreakWarning() {
        // Direct single-key calls (as most of this test class makes) legitimately violate the invariant to isolate
        // one key's translation -- PostgresSortClause#of only warns (never throws) so that usage keeps working.
        final ListAppender<ILoggingEvent> appender = attachAppender();
        try {
            PostgresSortClause.of(List.of(asc(fef.sortByAttribute("a"))));
            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage()).contains("thing-id tiebreak");
                    });
        } finally {
            detachAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        final ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PostgresSortClause.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(final ListAppender<ILoggingEvent> appender) {
        final ch.qos.logback.classic.Logger logbackLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PostgresSortClause.class);
        logbackLogger.detachAppender(appender);
        appender.stop();
    }

    // ============================================================================ helpers

    private RenderedSql renderSort(final SortOption... options) {
        final Select.Builder builder = baseSelect();
        PostgresSortClause.of(List.of(options)).applyOrdering(builder);
        return Sql.render(builder.build());
    }

    private static Select.Builder baseSelect() {
        return Sql.select().column(Sql.col("st", "thing_id")).from("search_things", "st");
    }

    private static String flatSortValue(final Integer rank, final BigDecimal num, final String text,
            final Boolean bool) {
        // A single flat sort key so the aliases are s0_*; a value-less RQL sort expression suffices.
        final PostgresSortClause clause =
                PostgresSortClause.of(List.of(asc(ThingsFieldExpressionFactory.of(MAPPINGS).sortByAttribute("k"))));
        final Map<String, Object> row = new HashMap<>();
        row.put("s0_rank", rank);
        row.put("s0_num", num);
        row.put("s0_text", text);
        row.put("s0_bool", bool);
        final JsonArray values = clause.toSortValues(accessor(row));
        return values.toString();
    }

    private static SortValueAccessor accessor(final Map<String, Object> row) {
        return new SortValueAccessor() {
            @Override
            public <T> T get(final String columnAlias, final Class<T> type) {
                return type.cast(row.get(columnAlias));
            }
        };
    }

    private RenderedSql sudo(final Criteria criteria) {
        return Sql.render(CreateSqlVisitor.sudoApply(criteria).predicate());
    }

    private static SortOption asc(final SortFieldExpression expression) {
        return new SortOption(expression, SortDirection.ASC);
    }

    private static SortOption desc(final SortFieldExpression expression) {
        return new SortOption(expression, SortDirection.DESC);
    }
}
