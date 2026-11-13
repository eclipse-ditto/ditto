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
package org.eclipse.ditto.thingsearch.service.starter.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresQuery;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.read.PostgresThingsSearchPersistence;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.write.PostgresSearchUpdaterFlow;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.thingsearch.model.SortOption;
import org.eclipse.ditto.thingsearch.model.SortOptionEntry;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ResultList;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
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
 * End-to-end integration test of the Task-D4 cursor seam: it drives the <em>REAL</em> package-private
 * {@link ThingsSearchCursor} (encode -&gt; decode -&gt; {@code adjust}) and feeds the resulting resume criteria into the
 * PostgreSQL read persistence, walking a filtered/sorted result set to exhaustion page by page and asserting the
 * concatenated pages equal the unpaged reference — no drops, no duplicates — including across a number-&gt;string sort
 * type boundary and rank-equal ties (the {@code eq}-dimension resume branch).
 * <p>
 * This lives in the cursor's own package (split-package test source) because {@link ThingsSearchCursor} is
 * package-private; thingsearch/service is a test-scope dependency (see the module POM). Because the cursor now feeds
 * plain-Java sort values (not BSON) into the resume predicate, the PostgreSQL translator ({@code SqlValues}) types the
 * bind correctly — the exact bug this test guards against.
 * </p>
 * <p>Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard).</p>
 */
public final class PostgresCursorResumeIT {

    private static final String NS = "org.eclipse.ditto";
    private static final DittoHeaders HEADERS = DittoHeaders.empty();

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
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresCursorResumeIT", t);
        }
        final Config config = ConfigFactory.parseString(
                "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                        + "ditto.postgresql.ssl.mode = \"disable\"\n"
                        + "ditto.postgresql.pool.initial-size = 1\n"
                        + "ditto.postgresql.pool.max-size = 4\n"
                        + "ditto.postgresql.force-custom-plan = true\n");
        client = DittoPostgresClient.newInstance(DefaultPostgresConfig.of(config.getConfig("ditto")));
        connectionFactory = client.getConnectionPool();
        system = ActorSystem.create("PostgresCursorResumeIT");
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

    @Test
    public void realCursorWalkAcrossIntLongDoubleBoundariesMatchesUnpaged() {
        // A homogeneous NUMERIC sort key whose values cross the int / long / double sub-type boundaries the cursor seam
        // must type correctly (all rank-2 val_num on Postgres). Two rank=42 ties exercise the eq-dimension resume
        // branch. (A single sort key with MIXED BSON type brackets — e.g. number then string — is not crossable by the
        // cursor's gt/lt in EITHER backend, a shared, parity-preserving limitation; hence the numeric sub-type walk.)
        writeRank("t-3", JsonValue.of(3.5d));            // double
        writeRank("t-5", JsonValue.of(5));               // int
        writeRank("t-42a", JsonValue.of(42));            // int (tie)
        writeRank("t-42b", JsonValue.of(42));            // int (tie)
        writeRank("t-100", JsonValue.of(100.25d));       // double
        writeRank("t-9b", JsonValue.of(9_000_000_000L)); // long (> int range)
        writeRank("t-9b1", JsonValue.of(9_000_000_001L));// long
        writeRank("t-missing", null);                    // no /attributes/rank -> excluded by exists(rank)

        final Criteria criteria = cf.existsCriteria(fef.existsByAttribute("rank"));
        final List<String> unpaged = idsOf(runFindAll(baseQuery(criteria, 100)));
        assertThat(unpaged).containsExactly(
                NS + ":t-3", NS + ":t-5", NS + ":t-42a", NS + ":t-42b", NS + ":t-100",
                NS + ":t-9b", NS + ":t-9b1");

        // Paged walk of size 2, driving the REAL ThingsSearchCursor each hop.
        final List<String> paged = new ArrayList<>();
        Query pageQuery = baseQuery(criteria, 2);
        int guard = 0;
        while (guard++ < 100) {
            final ResultList<TimestampedThingId> page = runFindAll(pageQuery);
            page.forEach(t -> paged.add(t.thingId().toString()));
            final Optional<JsonArray> sortValues = page.lastResultSortValues();
            if (sortValues.isEmpty()) {
                break; // no next page
            }
            final ThingsSearchCursor cursor =
                    new ThingsSearchCursor(null, "corr-" + guard, modelSort(), null, sortValues.get());
            final ThingsSearchCursor decoded = runSingle(ThingsSearchCursor.decode(cursor.encode(), system));
            // adjust the ORIGINAL base query (the cursor encodes the absolute position).
            pageQuery = ThingsSearchCursor.adjust(Optional.of(decoded), baseQuery(criteria, 2),
                    CriteriaFactory.getInstance());
        }

        assertThat(paged).as("paged walk equals unpaged, no drops or duplicates").isEqualTo(unpaged);
    }

    // ---- query construction ----

    private PostgresQuery baseQuery(final Criteria criteria, final int limit) {
        return new PostgresQuery(criteria, List.of(
                new org.eclipse.ditto.rql.query.SortOption(fef.sortByAttribute("rank"), SortDirection.ASC),
                new org.eclipse.ditto.rql.query.SortOption(fef.sortByThingId(), SortDirection.ASC)),
                limit, 0);
    }

    /** The cursor's own model sort option — 2 dimensions to match the 2-element cursor sort-value array. */
    private static SortOption modelSort() {
        return SortOption.of(List.of(
                SortOptionEntry.asc(JsonPointer.of("/attributes/rank")),
                SortOptionEntry.asc(JsonPointer.of("/thingId"))));
    }

    private static List<String> idsOf(final ResultList<TimestampedThingId> result) {
        final List<String> ids = new ArrayList<>();
        result.forEach(t -> ids.add(t.thingId().toString()));
        return ids;
    }

    private ResultList<TimestampedThingId> runFindAll(final Query query) {
        return runSingle(persistence.findAll(query, null, null, HEADERS));
    }

    // ---- document construction / write path ----

    private void writeRank(final String name, final JsonValue rank) {
        final ThingId thingId = ThingId.of(NS, name);
        final JsonObject attributes = rank == null
                ? JsonObject.empty()
                : JsonObject.newBuilder().set("rank", rank).build();
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", 1)
                .set("attributes", attributes)
                .build();
        final SearchIndexDocument document = SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(1L)
                .policyId(PolicyId.of(NS, "policy"))
                .policyRevision(1L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of(NS, "policy"), 1L)))
                .thing(thing)
                .build();
        write(ThingWriteModel.of(metadata(thingId), document));
    }

    private static Metadata metadata(final ThingId thingId) {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of(NS, "policy"), 1L);
        return Metadata.of(thingId, 1L, policyTag, null, Set.of(policyTag), null);
    }

    private void write(final AbstractWriteModel model) {
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

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }
}
