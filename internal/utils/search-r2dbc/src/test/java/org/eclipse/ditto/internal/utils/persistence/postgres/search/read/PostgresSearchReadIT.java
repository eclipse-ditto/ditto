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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.read;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQuery;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQueryBuilderFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresSortClause;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SearchQueryAssembler;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SelectiveLegs;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.api.SearchNamespaceReportResult;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.TimestampedThingId;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Read-path integration test (Task D4) against a real PostgreSQL (PG 16) via Testcontainers. Data is written by the
 * REAL write path ({@link PostgresSearchUpdaterFlow}) and read back through {@link PostgresThingsSearchPersistence},
 * covering the operator/auth matrix, {@code count}/{@code sudoCount}, the namespace report,
 * {@code recoverLastWriteModel} (incl. the emptied-out tombstone and absent cases), {@code findAllUnlimited} streaming,
 * that {@code plan_cache_mode = force_custom_plan} is active on a search pool, and that range legs of the selective-leg
 * CTE never change a result (array-valued paths included) while the runtime pick materialises the smaller leg.
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSearchReadIT {

    private static final String NS = "org.eclipse.ditto";
    private static final String G = "·g"; // grant key
    private static final DittoHeaders HEADERS = DittoHeaders.empty();
    private static final List<String> S1 = List.of("s1");

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static DittoPostgresClient client;
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(
            java.util.Map.of("thingId", "_id", "namespace", "_namespace"));
    private final CriteriaFactory cf = CriteriaFactory.getInstance();

    private PostgresSearchUpdaterFlow flow;
    private PostgresThingsSearchPersistence persistence;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchReadIT", t);
        }
        // A production-shaped client: one shared pool carrying plan_cache_mode=force_custom_plan (as
        // ditto-postgres-search.conf sets), backing both the real write path and the read persistence.
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        client = DittoPostgresClient.newInstance(DefaultPostgresConfig.of(config.getConfig("ditto")));
        connectionFactory = client.getConnectionPool();
        system = ActorSystem.create("PostgresSearchReadIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        if (client != null) {
            client.close();
        }
        POSTGRES.stop();
    }

    @Before
    public void bootstrap() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.of(client);
        persistence = PostgresThingsSearchPersistence.of(client);
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================ operator matrix (sudo)

    @Test
    public void operatorMatrixOnAttributes() {
        seedColorSizeCorpus();

        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"))))
                .containsExactlyInAnyOrder("red-1");
        // ne matches things that HAVE the leaf and no element equals (multikey parity) — not the missing one.
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("color"), cf.ne("red"))))
                .containsExactlyInAnyOrder("blue-2", "green-3");
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("size"), cf.gt(15))))
                .containsExactlyInAnyOrder("blue-2", "green-3");
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("size"), cf.in(List.of(10, 30)))))
                .containsExactlyInAnyOrder("red-1", "green-3");
        assertThat(sudoFind(cf.existsCriteria(fef.existsByAttribute("color"))))
                .containsExactlyInAnyOrder("red-1", "blue-2", "green-3");
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("color"), cf.like("re*"))))
                .containsExactlyInAnyOrder("red-1");
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByAttribute("color"), cf.ilike("RED"))))
                .containsExactlyInAnyOrder("red-1");
    }

    @Test
    public void emptyMatchesEmptyArrayAttribute() {
        write(doc("has-tags", attributes(JsonObject.newBuilder()
                .set("tags", org.eclipse.ditto.json.JsonArray.newBuilder().add("a").build()).build())), 1L);
        write(doc("empty-tags", attributes(JsonObject.newBuilder()
                .set("tags", org.eclipse.ditto.json.JsonArray.empty()).build())), 1L);
        assertThat(sudoFind(cf.emptyCriteria(fef.existsByAttribute("tags"))))
                .containsExactlyInAnyOrder("empty-tags");
    }

    @Test
    public void featurePropertyEqAndWildcard() {
        write(featureDoc("dev-1", "sensor", 21), 1L);
        write(featureDoc("dev-2", "sensor", 99), 1L);
        // non-wildcard feature path
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByFeatureProperty("sensor", "temp"), cf.eq(21))))
                .containsExactlyInAnyOrder("dev-1");
        // wildcard feature path (/features/*/properties/temp)
        assertThat(sudoFind(cf.fieldCriteria(fef.filterByFeatureProperty("*", "temp"), cf.eq(99))))
                .containsExactlyInAnyOrder("dev-2");
    }

    // ============================================================================================ auth granted/revoked

    @Test
    public void authGrantsAndRevokesVisibility() {
        // visible to s1: in global_read AND granted at root.
        write(docAuth("visible", attributes(colorObj("red")), List.of("s1"), rootGrant("s1")), 1L);
        // same value but not readable by s1 (global_read excludes s1, grant to s2 only).
        write(docAuth("hidden", attributes(colorObj("red")), List.of("s2"), rootGrant("s2")), 1L);

        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        assertThat(authFind(criteria, S1)).containsExactlyInAnyOrder("visible");
        // sudo sees both.
        assertThat(sudoFind(criteria)).containsExactlyInAnyOrder("visible", "hidden");
    }

    // ============================================================================================ sort

    @Test
    public void sortAscendingAndDescendingOnNumericAttribute() {
        write(doc("s-1", attributes(JsonObject.newBuilder().set("rank", 1).build())), 1L);
        write(doc("s-2", attributes(JsonObject.newBuilder().set("rank", 2).build())), 1L);
        write(doc("s-3", attributes(JsonObject.newBuilder().set("rank", 3).build())), 1L);
        final Criteria exists = cf.existsCriteria(fef.existsByAttribute("rank"));
        assertThat(findSorted(exists, fef.sortByAttribute("rank"), SortDirection.ASC))
                .containsExactly("s-1", "s-2", "s-3");
        assertThat(findSorted(exists, fef.sortByAttribute("rank"), SortDirection.DESC))
                .containsExactly("s-3", "s-2", "s-1");
    }

    @Test
    public void nullMissingAndEmptyArrayFoldToTheNullRank() {
        // explicit null
        write(doc("sn-b", attributes(JsonObject.newBuilder()
                .set("rank", org.eclipse.ditto.json.JsonValue.nullLiteral()).build())), 1L);
        // missing rank
        write(doc("sn-c", attributes(JsonObject.newBuilder().set("other", 1).build())), 1L);
        // empty array
        write(doc("sn-d", attributes(JsonObject.newBuilder()
                .set("rank", org.eclipse.ditto.json.JsonArray.empty()).build())), 1L);
        // a real value
        write(doc("sn-val", attributes(JsonObject.newBuilder().set("rank", 5).build())), 1L);

        // ASC: the null-rank cluster (explicit-null / missing / empty-array) sorts before the real value; DESC reverses
        // the whole-key direction so the real value comes first.
        final List<String> asc = findSorted(cf.any(), fef.sortByAttribute("rank"), SortDirection.ASC);
        final List<String> desc = findSorted(cf.any(), fef.sortByAttribute("rank"), SortDirection.DESC);
        // explicit-null, missing and empty-array all fold to the smallest rank -> they precede the real value under ASC.
        assertThat(asc.indexOf("sn-b")).isLessThan(asc.indexOf("sn-val"));
        assertThat(asc.indexOf("sn-c")).isLessThan(asc.indexOf("sn-val"));
        assertThat(asc.indexOf("sn-d")).isLessThan(asc.indexOf("sn-val"));
        // ...and follow it under DESC.
        assertThat(desc.indexOf("sn-b")).isGreaterThan(desc.indexOf("sn-val"));
        assertThat(desc.indexOf("sn-c")).isGreaterThan(desc.indexOf("sn-val"));
        assertThat(desc.indexOf("sn-d")).isGreaterThan(desc.indexOf("sn-val"));
    }

    @Test
    public void booleanSortKeyOrdersFalseBeforeTrue() {
        write(doc("b-false", attributes(JsonObject.newBuilder().set("flag", false).build())), 1L);
        write(doc("b-true", attributes(JsonObject.newBuilder().set("flag", true).build())), 1L);
        final Criteria exists = cf.existsCriteria(fef.existsByAttribute("flag"));
        assertThat(findSorted(exists, fef.sortByAttribute("flag"), SortDirection.ASC))
                .containsExactly("b-false", "b-true");
        assertThat(findSorted(exists, fef.sortByAttribute("flag"), SortDirection.DESC))
                .containsExactly("b-true", "b-false");
    }

    private List<String> findSorted(final Criteria criteria,
            final org.eclipse.ditto.rql.query.expression.SortFieldExpression sortExpression, final SortDirection dir) {
        final PostgresQuery q = new PostgresQuery(criteria, List.of(
                new SortOption(sortExpression, dir),
                new SortOption(fef.sortByThingId(), SortDirection.ASC)), 100, 0);
        return findIds(q, null);
    }

    // ============================================================================================ count / sudoCount

    @Test
    public void countAndSudoCountRespectAuth() {
        write(docAuth("red-visible", attributes(colorObj("red")), List.of("s1"), rootGrant("s1")), 1L);
        write(docAuth("red-hidden", attributes(colorObj("red")), List.of("s2"), rootGrant("s2")), 1L);

        final Criteria criteria = cf.fieldCriteria(fef.filterByAttribute("color"), cf.eq("red"));
        assertThat(runSingle(persistence.sudoCount(query(criteria), HEADERS))).isEqualTo(2L);
        assertThat(runSingle(persistence.count(query(criteria), S1, HEADERS))).isEqualTo(1L);
    }

    // ============================================================================================ namespace report

    @Test
    public void namespaceReportGroupsCounts() {
        write(doc(ThingId.of("ns.a", "t1"), attributes(colorObj("red"))), 1L);
        write(doc(ThingId.of("ns.a", "t2"), attributes(colorObj("blue"))), 1L);
        write(doc(ThingId.of("ns.b", "t3"), attributes(colorObj("green"))), 1L);

        final SearchNamespaceReportResult report =
                runSingle(persistence.generateNamespaceCountReport());
        assertThat(report.getNamespaceEntry("ns.a").getCount()).isEqualTo(2L);
        assertThat(report.getNamespaceEntry("ns.b").getCount()).isEqualTo(1L);
    }

    // ============================================================================================ recoverLastWriteModel

    @Test
    public void recoverRoundTripsFullWriteModel() {
        final SearchIndexDocument document =
                docAuth(ThingId.of(NS, "recover-1"), attributes(colorObj("red")), List.of("s1"), rootGrant("s1"), 5L);
        write(document, 5L);

        final AbstractWriteModel recovered = runSingle(persistence.recoverLastWriteModel(ThingId.of(NS, "recover-1")));
        assertThat(recovered).isInstanceOf(ThingWriteModel.class);
        final SearchIndexDocument recoveredDoc = ((ThingWriteModel) recovered).getDocument();
        assertThat(recoveredDoc.thingId().toString()).isEqualTo(document.thingId().toString());
        assertThat(recoveredDoc.revision()).isEqualTo(5L);
        assertThat(recoveredDoc.policyId()).isEqualTo(document.policyId());
        assertThat(recoveredDoc.referencedPolicies()).isEqualTo(document.referencedPolicies());
        assertThat(recoveredDoc.globalRead()).isEqualTo(document.globalRead());
        assertThat(recoveredDoc.thing()).isEqualTo(document.thing());
        assertThat(recoveredDoc.policyAuth()).isEqualTo(document.policyAuth());
    }

    @Test
    public void recoverReturnsTombstoneForEmptiedOut() {
        write(doc("tomb", attributes(colorObj("red"))), 1L);
        run(ThingWriteModel.ofEmptiedOut(metadata(ThingId.of(NS, "tomb"), 2L)));

        final AbstractWriteModel recovered = runSingle(persistence.recoverLastWriteModel(ThingId.of(NS, "tomb")));
        assertThat(recovered).isInstanceOf(ThingWriteModel.class);
        assertThat(((ThingWriteModel) recovered).isEmptiedOut()).isTrue();
    }

    @Test
    public void recoverReturnsDeleteModelForAbsentThing() {
        final AbstractWriteModel recovered =
                runSingle(persistence.recoverLastWriteModel(ThingId.of(NS, "never-written")));
        assertThat(recovered).isInstanceOf(ThingDeleteModel.class);
        assertThat(recovered.getMetadata().getThingRevision()).isEqualTo(-1L);
    }

    // ============================================================================================ findAllUnlimited

    @Test
    public void findAllUnlimitedStreamsMatchingThingIds() {
        // findAllUnlimited (StreamThings) always restricts by subjects; seed auth-visible docs and stream as s1.
        write(docAuth("u-1", attributes(colorObj("red")), List.of("s1"), rootGrant("s1")), 1L);
        write(docAuth("u-2", attributes(colorObj("blue")), List.of("s1"), rootGrant("s1")), 1L);
        write(docAuth("u-3", attributes(colorObj("green")), List.of("s1"), rootGrant("s1")), 1L);
        final List<String> streamed = run(persistence.findAllUnlimited(
                query(cf.existsCriteria(fef.existsByAttribute("color"))), S1, null, HEADERS)).stream()
                .map(ThingId::toString).collect(Collectors.toList());
        assertThat(streamed).containsExactlyInAnyOrder(NS + ":u-1", NS + ":u-2", NS + ":u-3");
    }

    @Test
    public void findAllUnlimitedStreamsMatchingThingIdsForParserProducedUnlimitedQuery() {
        // Regression test for the LIMIT-0 unlimited-sentinel bug: unlike the query() test helper above (which
        // hardcodes limit=100), this builds the query exactly the way production does for a real StreamThings
        // command — QueryParser -> queryBuilderFactory.newUnlimitedBuilder(criteria) ->
        // PostgresQueryBuilder.unlimited(criteria) — whose default limit is the Mongo-transcribed 0-sentinel
        // (MongoDB cursor.limit(0) == "no limit"; SQL LIMIT 0 == zero rows).
        write(docAuth("z-1", attributes(colorObj("red")), List.of("s1"), rootGrant("s1")), 1L);
        write(docAuth("z-2", attributes(colorObj("blue")), List.of("s1"), rootGrant("s1")), 1L);

        final Criteria criteria = cf.existsCriteria(fef.existsByAttribute("color"));
        final QueryBuilderFactory queryBuilderFactory = new PostgresQueryBuilderFactory(limitsConfig());
        final Query unlimitedQuery = queryBuilderFactory.newUnlimitedBuilder(criteria)
                .sort(List.of(new SortOption(fef.sortByThingId(), SortDirection.ASC)))
                .build();
        assertThat(unlimitedQuery.getLimit()).isZero();

        final List<String> streamed = run(persistence.findAllUnlimited(unlimitedQuery, S1, null, HEADERS)).stream()
                .map(ThingId::toString).collect(Collectors.toList());
        assertThat(streamed).containsExactlyInAnyOrder(NS + ":z-1", NS + ":z-2");
    }

    private static LimitsConfig limitsConfig() {
        return new LimitsConfig() {
            @Override
            public long getThingsMaxSize() {
                return 0;
            }

            @Override
            public long getPoliciesMaxSize() {
                return 0;
            }

            @Override
            public long getMessagesMaxSize() {
                return 0;
            }

            @Override
            public int getThingsSearchDefaultPageSize() {
                return 25;
            }

            @Override
            public int getThingsSearchMaxPageSize() {
                return 200;
            }

            @Override
            public int getPolicyImportsLimit() {
                return 0;
            }
        };
    }

    // ============================================================================================ force_custom_plan

    @Test
    public void forceCustomPlanActiveOnSearchPool() {
        // The persistence reads over this very pool; SHOW proves the wpath-plancache guard (plan §3.5) is active on it.
        final String mode = Mono.usingWhen(client.getConnectionPool().create(),
                        connection -> Mono.from(connection.createStatement("SHOW plan_cache_mode").execute())
                                .flatMap(result -> Mono.from(result.map((row, meta) -> row.get(0, String.class)))),
                        Connection::close)
                .block(Duration.ofSeconds(20));
        assertThat(mode).isEqualTo("force_custom_plan");
    }

    // ============================================================================================ selective range leg

    private static final String MULTI = SystemSearchIndex.MULTI_VALUED.createStatement();

    private PostgresThingsSearchPersistence withRangeLegs() {
        return PostgresThingsSearchPersistence.forConnectionFactory(connectionFactory, 100, () -> true);
    }

    private PostgresThingsSearchPersistence pointLegsOnly() {
        return PostgresThingsSearchPersistence.forConnectionFactory(connectionFactory, 100, () -> false);
    }

    private void seedRangeCorpus() {
        // arrays that straddle [91700, 93000] without an element inside it, and ones that do / do not match
        write(docAuth("straddle", attributes(JsonObject.newBuilder()
                .set("region", JsonArray.of("[91621, 91214, 93186]")).set("pair", JsonArray.of("[6, 2]"))
                .set("kind", "common").build()), S1, rootGrant("s1")), 1L);
        write(docAuth("inside", attributes(JsonObject.newBuilder().set("region", JsonArray.of("[91750]"))
                .set("kind", "common").build()), S1, rootGrant("s1")), 1L);
        write(docAuth("scalar-inside", attributes(JsonObject.newBuilder().set("region", 92500)
                .set("pair", 4).set("kind", "rare").build()), S1, rootGrant("s1")), 1L);
        write(doc("above", attributes(JsonObject.newBuilder().set("region", JsonArray.of("[95000, 96000]"))
                .set("pair", JsonArray.of("[7, 8]")).set("kind", "common").build())), 1L);
        write(doc("text", attributes(JsonObject.newBuilder().set("region", "92000").build())), 1L);
        write(featureArrayDoc("feat-straddle", "f", JsonArray.of("[10, 30]")), 1L);   // features/f/properties/v
        write(featureArrayDoc("feat-inside", "f", JsonArray.of("[20]")), 1L);
    }

    @Test
    public void rangeLegsNeverChangeFindOrCountResultsOnArrayValuedPaths() {
        seedRangeCorpus();
        runDdl(MULTI);
        final List<Criteria> filters = List.of(
                cf.and(List.of(ge("region", 91700), le("region", 93000))),        // straddle: 1,327 -> 909 class
                cf.and(List.of(gt("region", 91621), lt("region", 93186))),
                cf.and(List.of(ge("pair", 5), le("pair", 3))),                     // empty interval: [6, 2] matches
                cf.and(List.of(eqAttr("kind", "common"), ge("region", 91700), le("region", 93000))),
                cf.and(List.of(eqAttr("kind", "rare"), ge("region", 0), le("region", 1_000_000))),
                cf.and(List.of(geFeature("f", "v", 15), leFeature("f", "v", 25))));
        for (final Criteria criteria : filters) {
            assertSameResults(criteria);
        }
        // the two pairs still exercise a range leg (the lone ranges compare identical statements now)
        for (final Criteria pair : List.of(filters.get(3), filters.get(4))) {
            assertThat(SearchQueryAssembler.count(pair, null, 0L, 0L, SelectiveLegs.POINT_AND_RANGE).sql())
                    .contains("sel_pick").contains("sfc.ord = 1");
        }
        // the straddling thing is matched (MongoDB semantics), which a single-row range leg would lose
        assertThat(sudoFindWith(withRangeLegs(), filters.get(0))).contains("straddle");
        assertThat(sudoFindWith(withRangeLegs(), filters.get(2))).contains("straddle");
        assertThat(sudoFindWith(withRangeLegs(), filters.get(5))).contains("feat-straddle");
        assertThat(authFindWith(withRangeLegs(), filters.get(0))).as("the auth form is not empty")
                .contains("straddle");
    }

    @Test
    public void rangeLegsAreCorrectEvenWithoutTheIndex() {
        seedRangeCorpus();   // no sf_multi: slower, never different
        assertSameResults(cf.and(List.of(ge("region", 91700), le("region", 93000))));
    }

    @Test
    public void thePickMaterialisesTheLegWithFewerIndexEntries() {
        for (int i = 0; i < 60; i++) {   // "common": 59 things; temperature 10..69
            write(doc("t" + i, attributes(JsonObject.newBuilder().set("city", i == 0 ? "rare" : "common")
                    .set("temp", 10 + i).build())), 1L);
        }
        runDdl(MULTI);
        // common city (59) vs a 3-wide range (3) -> range leg
        assertThat(executedSelLegBranches(cf.and(List.of(eqAttr("city", "common"), ge("temp", 20), le("temp", 22)))))
                .containsExactly(false, true, true);
        // rare city (1) vs a wide range (60, counted up to 2) -> point leg
        assertThat(executedSelLegBranches(cf.and(List.of(eqAttr("city", "rare"), ge("temp", 0), le("temp", 100)))))
                .containsExactly(true, false, false);
    }

    @Test
    public void theMultiValuedBranchUsesTheSystemIndex() {
        seedRangeCorpus();
        runDdl(MULTI);
        runDdl("VACUUM ANALYZE search_flat");
        // a range leg only exists next to a point leg
        final String plan =
                explainText(cf.and(List.of(eqAttr("kind", "common"), ge("region", 91700), le("region", 93000))));
        assertThat(plan).contains("Index Only Scan using sf_multi");
    }

    /** Clones of a lone range's matching thing: tens of thousands, the setting in which a grouped leg was slowest. */
    private static final int LONE_RANGE_CLONES = 40_000;

    @Test
    public void aLoneRangeIsNeverMaterialisedEvenWithTensOfThousandsOfMatches() {
        // matches [0, 10] with no element inside it (MongoDB semantics)
        write(docAuth("straddle-n", attributes(JsonObject.newBuilder().set("n", JsonArray.of("[-5, 20]")).build()),
                S1, rootGrant("s1")), 1L);
        write(docAuth("template", attributes(JsonObject.newBuilder().set("n", 5).build()), S1, rootGrant("s1")), 1L);
        write(docAuth("outside", attributes(JsonObject.newBuilder().set("n", 50).build()), S1, rootGrant("s1")), 1L);
        runDdl(MULTI);
        cloneThing("template", 1, LONE_RANGE_CLONES);
        runDdl("ANALYZE search_things");
        runDdl("ANALYZE search_flat");
        final Criteria lone = cf.and(List.of(ge("n", 0), le("n", 10)));
        final PostgresSortClause byThingId =
                PostgresSortClause.of(List.of(new SortOption(fef.sortByThingId(), SortDirection.ASC)));
        final RenderedSql sudoFind =
                SearchQueryAssembler.findAll(lone, null, byThingId, 26L, 0L, SelectiveLegs.POINT_AND_RANGE);

        assertThat(sudoFind.sql())
                .isEqualTo(SearchQueryAssembler.findAll(lone, null, byThingId, 26L, 0L, SelectiveLegs.POINT).sql())
                .doesNotContain("sel_leg").doesNotContain("GROUP BY");
        // no "Aggregate" check: a semi-join may unique-ify its inner side with a HashAggregate on its own
        assertThat(String.join("\n", explain("EXPLAIN (ANALYZE) ", sudoFind, false)))
                .doesNotContain("CTE sel_leg").doesNotContain("CTE Scan");
        assertSameResults(lone);
        // the clones, the template and the straddling array; not "outside"
        assertThat(runSingle(withRangeLegs().sudoCount(countQuery(lone), HEADERS))).isEqualTo(LONE_RANGE_CLONES + 2L);
    }

    @Test
    public void theGroupedPickKeepsResultsWhenTheRangeWins() {
        seedRangeCorpus();
        // kind = common: straddle, inside, above + 2 padding things = 5 entries. The region range [91700, 93000]:
        // inside and scalar-inside in range + the ord = 1 rows of straddle and above = 4. So the range wins (5 > 4).
        write(doc("pad-1", attributes(JsonObject.newBuilder().set("kind", "common").build())), 1L);
        write(doc("pad-2", attributes(JsonObject.newBuilder().set("kind", "common").build())), 1L);
        runDdl(MULTI);
        final Criteria pick = cf.and(List.of(eqAttr("kind", "common"), ge("region", 91700), le("region", 93000)));

        assertThat(executedSelLegBranches(pick)).containsExactly(false, true, true);
        assertSameResults(pick);
        // straddle matches through two different elements; above has no element <= 93000
        assertThat(sudoFindWith(withRangeLegs(), pick)).containsExactly("inside", "straddle");
    }

    private void assertSameResults(final Criteria criteria) {
        final PostgresQuery query = query(criteria);
        assertThat(ids(withRangeLegs().findAll(query, null, null, HEADERS)))
                .as("sudo find %s", criteria).isEqualTo(ids(pointLegsOnly().findAll(query, null, null, HEADERS)));
        assertThat(ids(withRangeLegs().findAll(query, S1, null, HEADERS)))
                .as("auth find %s", criteria).isEqualTo(ids(pointLegsOnly().findAll(query, S1, null, HEADERS)));
        final PostgresQuery count = countQuery(criteria);
        assertThat(runSingle(withRangeLegs().sudoCount(count, HEADERS)))
                .as("sudo count %s", criteria).isEqualTo(runSingle(pointLegsOnly().sudoCount(count, HEADERS)));
        assertThat(runSingle(withRangeLegs().count(count, S1, HEADERS)))
                .as("auth count %s", criteria).isEqualTo(runSingle(pointLegsOnly().count(count, S1, HEADERS)));
    }

    /**
     * The plain count (no skip/limit), which carries the selective-leg CTE; {@link #query} (limit 100) would take the
     * bounded form, which has none.
     */
    private PostgresQuery countQuery(final Criteria criteria) {
        return new PostgresQuery(criteria, List.of(new SortOption(fef.sortByThingId(), SortDirection.ASC)), 0, 0);
    }

    /**
     * Copies thing {@code <ns>:<template>} (its search_things row and every search_flat row) as
     * {@code <ns>:clone-<i>} for every {@code i} in {@code [from, to]}.
     */
    private static void cloneThing(final String template, final int from, final int to) {
        final String ids = "generate_series(" + from + ", " + to + ") AS i";
        runDdl("INSERT INTO search_things SELECT (jsonb_populate_record(t, jsonb_build_object('thing_id', "
                + "replace(t.thing_id, ':" + template + "', ':clone-' || i)))).* FROM search_things t, " + ids
                + " WHERE t.thing_id LIKE '%:" + template + "'");
        runDdl("INSERT INTO search_flat SELECT (jsonb_populate_record(f, jsonb_build_object('thing_id', "
                + "replace(f.thing_id, ':" + template + "', ':clone-' || i)))).* FROM search_flat f, " + ids
                + " WHERE f.thing_id LIKE '%:" + template + "'");
    }

    private List<String> sudoFindWith(final PostgresThingsSearchPersistence searchPersistence,
            final Criteria criteria) {
        return ids(searchPersistence.findAll(query(criteria), null, null, HEADERS));
    }

    private List<String> authFindWith(final PostgresThingsSearchPersistence searchPersistence,
            final Criteria criteria) {
        return ids(searchPersistence.findAll(query(criteria), S1, null, HEADERS));
    }

    private List<String> ids(final Source<ResultList<TimestampedThingId>, ?> page) {
        return runSingle(page).stream().map(t -> t.thingId().getName()).collect(Collectors.toList());
    }

    private Criteria ge(final String attribute, final Object value) {
        return cf.fieldCriteria(fef.filterByAttribute(attribute), cf.ge(value));
    }

    private Criteria le(final String attribute, final Object value) {
        return cf.fieldCriteria(fef.filterByAttribute(attribute), cf.le(value));
    }

    private Criteria gt(final String attribute, final Object value) {
        return cf.fieldCriteria(fef.filterByAttribute(attribute), cf.gt(value));
    }

    private Criteria lt(final String attribute, final Object value) {
        return cf.fieldCriteria(fef.filterByAttribute(attribute), cf.lt(value));
    }

    private Criteria eqAttr(final String attribute, final Object value) {
        return cf.fieldCriteria(fef.filterByAttribute(attribute), cf.eq(value));
    }

    private Criteria geFeature(final String featureId, final String property, final Object value) {
        return cf.fieldCriteria(fef.filterByFeatureProperty(featureId, property), cf.ge(value));
    }

    private Criteria leFeature(final String featureId, final String property, final Object value) {
        return cf.fieldCriteria(fef.filterByFeatureProperty(featureId, property), cf.le(value));
    }

    /**
     * Runs {@code EXPLAIN (ANALYZE, FORMAT JSON)} of the sudo count with range legs and returns, for each
     * {@code UNION ALL} branch of {@code sel_leg} in order (point, in-range, multi-valued), whether its scan ran: a
     * branch gated off by its one-time filter is never executed.
     */
    private static List<Boolean> executedSelLegBranches(final Criteria criteria) {
        final RenderedSql count = SearchQueryAssembler.count(criteria, null, 0L, 0L, SelectiveLegs.POINT_AND_RANGE);
        final String json = String.join("", explain("EXPLAIN (ANALYZE, FORMAT JSON) ", count, false));
        final JsonObject root = JsonFactory.readFrom(json).asArray().get(0).orElseThrow().asObject();
        final JsonObject cte = findNode(root.getValue("Plan").orElseThrow().asObject(), "CTE sel_leg")
                .orElseThrow(() -> new AssertionError("no CTE sel_leg in " + json));
        final JsonObject append = "Append".equals(cte.getValue("Node Type").map(JsonValue::asString).orElse(""))
                ? cte : findNodeOfType(cte, "Append").orElseThrow(() -> new AssertionError("no Append in " + cte));
        final List<Boolean> executed = new ArrayList<>();
        for (final JsonValue branch : children(append)) {
            executed.add(scanRan(branch.asObject()));
        }
        return executed;
    }

    /** A branch is {@code Result (One-Time Filter) -> scan}: the scan's loops tell whether the filter let it run. */
    private static boolean scanRan(final JsonObject branch) {
        if (branch.contains("One-Time Filter")) {
            for (final JsonValue child : children(branch)) {
                if ("Outer".equals(child.asObject().getValue("Parent Relationship").map(JsonValue::asString)
                        .orElse(""))) {
                    return loops(child.asObject()) > 0;
                }
            }
        }
        return loops(branch) > 0;
    }

    private static long loops(final JsonObject node) {
        return node.getValue("Actual Loops").map(JsonValue::asLong).orElse(0L);
    }

    private static List<JsonValue> children(final JsonObject node) {
        return node.getValue("Plans").map(value -> value.asArray().stream().toList()).orElse(List.of());
    }

    private static Optional<JsonObject> findNode(final JsonObject node, final String subplanName) {
        if (subplanName.equals(node.getValue("Subplan Name").map(JsonValue::asString).orElse(""))) {
            return Optional.of(node);
        }
        return children(node).stream().map(child -> findNode(child.asObject(), subplanName))
                .flatMap(Optional::stream).findFirst();
    }

    private static Optional<JsonObject> findNodeOfType(final JsonObject node, final String nodeType) {
        for (final JsonValue child : children(node)) {
            if (nodeType.equals(child.asObject().getValue("Node Type").map(JsonValue::asString).orElse(""))) {
                return Optional.of(child.asObject());
            }
            final Optional<JsonObject> deeper = findNodeOfType(child.asObject(), nodeType);
            if (deeper.isPresent()) {
                return deeper;
            }
        }
        return Optional.empty();
    }

    /** EXPLAIN of the sudo count with range legs, with {@code enable_seqscan = off} (index eligibility). */
    private static String explainText(final Criteria criteria) {
        final RenderedSql count = SearchQueryAssembler.count(criteria, null, 0L, 0L, SelectiveLegs.POINT_AND_RANGE);
        return String.join("\n", explain("EXPLAIN ", count, true));
    }

    private static List<String> explain(final String prefix, final RenderedSql rendered, final boolean noSeqscan) {
        return Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Mono.from(connection.beginTransaction())
                                .thenMany(noSeqscan
                                        ? Flux.from(connection.createStatement("SET LOCAL enable_seqscan = off")
                                        .execute()).flatMap(Result::getRowsUpdated)
                                        : Flux.empty())
                                .thenMany(Flux.from(rendered.applyTo(connection.createStatement(prefix
                                                + rendered.sql())).execute())
                                        .flatMap(result -> result.map((row, meta) -> row.get(0, String.class))))
                                .collectList()
                                .flatMapMany(rows -> Mono.from(connection.rollbackTransaction())
                                        .thenMany(Flux.fromIterable(rows))),
                        Connection::close)
                .collectList()
                .block(Duration.ofSeconds(30));
    }

    // ============================================================================================ helpers

    private void seedColorSizeCorpus() {
        write(doc("red-1", attributes(JsonObject.newBuilder().set("color", "red").set("size", 10).build())), 1L);
        write(doc("blue-2", attributes(JsonObject.newBuilder().set("color", "blue").set("size", 20).build())), 1L);
        write(doc("green-3", attributes(JsonObject.newBuilder().set("color", "green").set("size", 30).build())), 1L);
    }

    private List<String> sudoFind(final Criteria criteria) {
        return findIds(query(criteria), null);
    }

    private List<String> authFind(final Criteria criteria, final List<String> subjects) {
        return findIds(query(criteria), subjects);
    }

    private List<String> findIds(final PostgresQuery query, final List<String> subjects) {
        final ResultList<TimestampedThingId> result =
                runSingle(persistence.findAll(query, subjects, null, HEADERS));
        return result.stream().map(t -> t.thingId().getName()).collect(Collectors.toList());
    }

    private PostgresQuery query(final Criteria criteria) {
        return new PostgresQuery(criteria, List.of(new SortOption(fef.sortByThingId(), SortDirection.ASC)), 100, 0);
    }

    // ---- document construction ----

    private static JsonObject colorObj(final String color) {
        return JsonObject.newBuilder().set("color", color).build();
    }

    private static JsonObject attributes(final JsonObject attributes) {
        return JsonObject.newBuilder().set("attributes", attributes).build();
    }

    private static JsonObject rootGrant(final String... subjects) {
        final org.eclipse.ditto.json.JsonObjectBuilder grants = JsonObject.newBuilder();
        for (final String subject : subjects) {
            grants.set(subject, JsonObject.empty());
        }
        return JsonObject.newBuilder().set(G, grants.build()).build();
    }

    private SearchIndexDocument doc(final String name, final JsonObject body) {
        return doc(ThingId.of(NS, name), body);
    }

    private SearchIndexDocument doc(final ThingId thingId, final JsonObject body) {
        return docAuth(thingId, body, List.of(), JsonObject.empty());
    }

    private SearchIndexDocument docAuth(final String name, final JsonObject body, final List<String> globalRead,
            final JsonObject policyAuth) {
        return docAuth(ThingId.of(NS, name), body, globalRead, policyAuth);
    }

    private SearchIndexDocument docAuth(final ThingId thingId, final JsonObject body, final List<String> globalRead,
            final JsonObject policyAuth) {
        return docAuth(thingId, body, globalRead, policyAuth, 1L);
    }

    private SearchIndexDocument docAuth(final ThingId thingId, final JsonObject body, final List<String> globalRead,
            final JsonObject policyAuth, final long revision) {
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", (int) revision)
                .setAll(body)
                .build();
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(revision)
                .policyId(PolicyId.of(NS, "policy"))
                .policyRevision(1L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of(NS, "policy"), 1L)))
                .globalRead(globalRead)
                .thing(thing)
                .policyAuth(policyAuth)
                .build();
    }

    private SearchIndexDocument featureDoc(final String name, final String featureId, final int temp) {
        final ThingId thingId = ThingId.of(NS, name);
        final JsonObject features = JsonObject.newBuilder()
                .set(featureId, JsonObject.newBuilder()
                        .set("properties", JsonObject.newBuilder().set("temp", temp).build()).build())
                .build();
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", 1)
                .set("features", features)
                .build();
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(1L)
                .policyId(PolicyId.of(NS, "policy"))
                .policyRevision(1L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of(NS, "policy"), 1L)))
                .thing(thing)
                .build();
    }

    /** A thing with the array {@code values} at {@code features/<featureId>/properties/v}, visible to s1. */
    private SearchIndexDocument featureArrayDoc(final String name, final String featureId, final JsonArray values) {
        final ThingId thingId = ThingId.of(NS, name);
        final JsonObject features = JsonObject.newBuilder()
                .set(featureId, JsonObject.newBuilder()
                        .set("properties", JsonObject.newBuilder().set("v", values).build()).build())
                .build();
        return docAuth(thingId, JsonObject.newBuilder().set("features", features).build(), S1, rootGrant("s1"));
    }

    private static Metadata metadata(final ThingId thingId, final long revision) {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of(NS, "policy"), 1L);
        return Metadata.of(thingId, revision, policyTag, null, Set.of(policyTag), null);
    }

    private void write(final SearchIndexDocument document, final long revision) {
        run(ThingWriteModel.of(metadata(document.thingId(), revision), document));
    }

    private void run(final AbstractWriteModel model) {
        try {
            Source.single(new UpdaterData(model, model))
                    .via(flow.create())
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("write failed", e);
        }
    }

    private <T> T runSingle(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.head(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

    private <T> List<T> run(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.seq(), system).toCompletableFuture().get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }
}
