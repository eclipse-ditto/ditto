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
import java.util.function.Function;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresAggregationQuery.GroupKey;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.AggregateThingsMetricsResponse;
import org.junit.Test;

/**
 * Golden unit tests for {@link PostgresAggregationQuery} — the operator-metrics {@code $match}+{@code $group} → SQL
 * transcription (Phase E). Two obligations are pinned here, both DB-free:
 * <ol>
 *   <li><b>SQL + binds</b> for the group-by extraction ({@code #>>}), the namespace {@code $match} term and the sudo
 *       filter (no auth), across the multi-key / empty-filter / namespaces / no-group-key cases.</li>
 *   <li><b>Emitted-JsonObject shape</b> golden vs the captured Mongo convention {@code {"_id": {…}, "count": n}},
 *       including the consumer round-trip through {@link AggregateThingsMetricsResponse#getGroupedBy()} /
 *       {@link AggregateThingsMetricsResponse#getResult()} (the sole consumer, which stringifies every group value and
 *       reads {@code count} as a long).</li>
 * </ol>
 */
public final class PostgresAggregationQueryTest {

    private static final Map<String, String> MAPPINGS = Map.of("thingId", "_id", "namespace", "_namespace");
    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(MAPPINGS);
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    // ============================================================================================ SQL + binds goldens

    @Test
    public void namespacesAndEmptyFilterSingleGroupKey() {
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location",
                List.of("attributes", "coffeemaker", "location")));
        final RenderedSql r = PostgresAggregationQuery.assemble(cf.any(), List.of("org.eclipse.ditto"), keys);

        assertThat(r.sql()).isEqualTo(
                "SELECT (st.thing #>> $1::text[]) AS g0, count(*) AS count FROM search_things st "
                        + "WHERE (st.namespace = ANY($2::text[]) AND true) GROUP BY 1");
        assertThat(r.bindValues()).hasSize(2);
        assertThat((String[]) r.bindValues().get(0))
                .containsExactly("attributes", "coffeemaker", "location");
        assertThat((String[]) r.bindValues().get(1)).containsExactly("org.eclipse.ditto");
    }

    @Test
    public void noNamespacesEmptyFilterMultiGroupKey() {
        // Two keys; grouping columns projected in the given order, GROUP BY by output-column ordinal, count(*) last.
        final List<GroupKey> keys = List.of(
                new GroupKey("g0", "loc", List.of("attributes", "loc")),
                new GroupKey("g1", "typ", List.of("attributes", "typ")));
        final RenderedSql r = PostgresAggregationQuery.assemble(cf.any(), List.of(), keys);

        assertThat(r.sql()).isEqualTo(
                "SELECT (st.thing #>> $1::text[]) AS g0, (st.thing #>> $2::text[]) AS g1, count(*) AS count "
                        + "FROM search_things st WHERE true GROUP BY 1, 2");
        assertThat((String[]) r.bindValues().get(0)).containsExactly("attributes", "loc");
        assertThat((String[]) r.bindValues().get(1)).containsExactly("attributes", "typ");
    }

    @Test
    public void noGroupKeysEmitsSingleGroupNoGroupByClause() {
        final RenderedSql r = PostgresAggregationQuery.assemble(cf.any(), List.of(), List.of());
        // Empty _id (Mongo group(new Document())): count(*) over the whole match, no GROUP BY.
        assertThat(r.sql()).isEqualTo("SELECT count(*) AS count FROM search_things st WHERE true");
        assertThat(r.bindValues()).isEmpty();
    }

    @Test
    public void noGroupKeysWithNamespaces() {
        final RenderedSql r = PostgresAggregationQuery.assemble(cf.any(), List.of("ns.a", "ns.b"), List.of());
        assertThat(r.sql()).isEqualTo("SELECT count(*) AS count FROM search_things st "
                + "WHERE (st.namespace = ANY($1::text[]) AND true)");
        assertThat((String[]) r.bindValues().get(0)).containsExactly("ns.a", "ns.b");
    }

    @Test
    public void filterCriteriaTranslatedInSudoFormNoAuth() {
        // eq(attributes/color,"red") -> a D2 flat-table EXISTS probe; the aggregation adds NO auth term (sudo, Mongo:109).
        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        final RenderedSql r = PostgresAggregationQuery.assemble(criteria, List.of("org.eclipse.ditto"), keys);

        assertThat(r.sql()).startsWith("SELECT (st.thing #>> $1::text[]) AS g0, count(*) AS count "
                + "FROM search_things st WHERE (st.namespace = ANY($2::text[]) AND ");
        assertThat(r.sql()).endsWith(" GROUP BY 1");
        // sudo form: no global-read / auth-tree recheck is appended (that only appears in the non-sudo read path).
        assertThat(r.sql()).doesNotContain("global_read");
        assertThat(r.sql()).doesNotContain("policy_auth");
        // the flat-table value probe of the filter is present with its bound value.
        assertThat(r.sql()).contains("search_flat");
        assertThat(r.bindValues()).contains("red");
    }

    // ================================================================================= emitted-shape goldens (vs Mongo)

    @Test
    public void emittedShapeMatchesMongoConventionForStringGroupValue() {
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> "Berlin", 5L);
        // Byte-identical to what Mongo's JsonFactory.newObject(document.toJson()) yields for a string-valued _id key.
        assertThat(json.toString()).isEqualTo("{\"_id\":{\"location\":\"Berlin\"},\"count\":5}");
    }

    @Test
    public void missingGroupValueEmittedAsJsonNull() {
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        // SQL NULL (missing path) -> JSON null, mirroring Mongo's absent-field -> null grouping.
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> null, 3L);
        assertThat(json.toString()).isEqualTo("{\"_id\":{\"location\":null},\"count\":3}");
    }

    @Test
    public void numericGroupValueRendersAsJsonStringDocumentedTextDivergence() {
        // DOCUMENTED divergence: #>> text extraction yields "3" (a JSON string) where Mongo's typed _id would carry the
        // number 3. Invisible to the consumer (both formatAsString() to "3"); pinned so the decision stays intentional.
        final List<GroupKey> keys = List.of(new GroupKey("g0", "floor", List.of("attributes", "floor")));
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> "3", 5L);
        assertThat(json.toString()).isEqualTo("{\"_id\":{\"floor\":\"3\"},\"count\":5}");
    }

    @Test
    public void noGroupKeysEmitsEmptyIdObject() {
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(List.of(), alias -> null, 7L);
        assertThat(json.toString()).isEqualTo("{\"_id\":{},\"count\":7}");
    }

    @Test
    public void emptyStringGroupValueEmittedAsEmptyJsonString() {
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> "", 1L);
        assertThat(json.toString()).isEqualTo("{\"_id\":{\"location\":\"\"},\"count\":1}");
    }

    @Test
    public void multiKeyEmittedInAscendingKeyOrder() {
        final List<GroupKey> keys = List.of(
                new GroupKey("g0", "loc", List.of("attributes", "loc")),
                new GroupKey("g1", "typ", List.of("attributes", "typ")));
        final Function<String, String> texts = alias -> alias.equals("g0") ? "Berlin" : "coffee";
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, texts, 2L);
        assertThat(json.toString()).isEqualTo("{\"_id\":{\"loc\":\"Berlin\",\"typ\":\"coffee\"},\"count\":2}");
    }

    // =============================================================================== consumer round-trip (the contract)

    @Test
    public void consumerParsesGroupedByAndResultFromEmittedShape() {
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> "Berlin", 5L);

        final AggregateThingsMetricsResponse response =
                AggregateThingsMetricsResponse.of(json, DittoHeaders.empty(), "my-metric");
        assertThat(response.getGroupedBy()).containsExactly(Map.entry("location", "Berlin"));
        assertThat(response.getResult()).contains(5L);
    }

    @Test
    public void consumerReadsNumericGroupValueIdenticallyToMongo() {
        // The text-extraction divergence is consumer-invisible: getGroupedBy() stringifies to "3" either way.
        final List<GroupKey> keys = List.of(new GroupKey("g0", "floor", List.of("attributes", "floor")));
        final JsonObject json = PostgresAggregationQuery.toAggregationJson(keys, alias -> "3", 5L);
        final AggregateThingsMetricsResponse response =
                AggregateThingsMetricsResponse.of(json, DittoHeaders.empty(), "m");
        assertThat(response.getGroupedBy()).containsExactly(Map.entry("floor", "3"));
        assertThat(response.getResult()).contains(5L);
    }

    // ================================================================================= zero-match no-group suppression

    @Test
    public void isVanishedNoGroupRowTrueForZeroCountNoGroupKeys() {
        // The GROUP-BY-less count(*) always returns one row (count 0) over zero matches; Mongo's $group would emit
        // none. This is the row the caller (PostgresThingsAggregationPersistence) must suppress.
        assertThat(PostgresAggregationQuery.isVanishedNoGroupRow(List.of(), 0L)).isTrue();
    }

    @Test
    public void isVanishedNoGroupRowFalseForPositiveCountNoGroupKeys() {
        // A real match: the single all-rows group is genuine and must be emitted.
        assertThat(PostgresAggregationQuery.isVanishedNoGroupRow(List.of(), 3L)).isFalse();
    }

    @Test
    public void isVanishedNoGroupRowNeverSuppressesAGroupedRowEvenIfDefensivelyZeroCount() {
        // A real GROUP BY groups actual rows -> zero matching rows already yields zero SQL rows (Mongo parity by
        // construction); this predicate must never additionally suppress a grouped row.
        final List<GroupKey> keys = List.of(new GroupKey("g0", "location", List.of("attributes", "location")));
        assertThat(PostgresAggregationQuery.isVanishedNoGroupRow(keys, 0L)).isFalse();
        assertThat(PostgresAggregationQuery.isVanishedNoGroupRow(keys, 5L)).isFalse();
    }

    // ============================================================================================ groupKeysOf helper

    @Test
    public void groupKeysOfSortsByOutputKeyAndSplitsRawSegments() {
        final List<GroupKey> keys = PostgresAggregationQuery.groupKeysOf(
                Map.of("z", "a/b", "a", "attributes/coffeemaker/location"));
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0).alias()).isEqualTo("g0");
        assertThat(keys.get(0).outputKey()).isEqualTo("a");
        assertThat(keys.get(0).segments()).containsExactly("attributes", "coffeemaker", "location");
        assertThat(keys.get(1).alias()).isEqualTo("g1");
        assertThat(keys.get(1).outputKey()).isEqualTo("z");
        assertThat(keys.get(1).segments()).containsExactly("a", "b");
    }

    @Test
    public void groupKeysOfDropsLeadingSlashSegment() {
        final List<GroupKey> keys = PostgresAggregationQuery.groupKeysOf(Map.of("k", "/attributes/x"));
        assertThat(keys.get(0).segments()).containsExactly("attributes", "x");
    }
}
