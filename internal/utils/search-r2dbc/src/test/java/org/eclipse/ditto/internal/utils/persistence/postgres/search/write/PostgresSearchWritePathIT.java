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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.FlatRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.flatten.ThingFlattener;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.SearchIndexDocument;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResultStatus;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link PostgresSearchUpdaterFlow} — the per-thing transactional write engine (unconditional
 * upsert + v1.5 anti-join flat maintenance) against a real PostgreSQL (PG 16) via Testcontainers.
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an unreachable
 * Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSearchWritePathIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;
    private static ActorSystem system;

    private PostgresSearchUpdaterFlow flow;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchWritePathIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        system = ActorSystem.create("PostgresSearchWritePathIT");
    }

    @AfterClass
    public static void stopContainer() {
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    @Before
    public void bootstrapSchema() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        flow = PostgresSearchUpdaterFlow.forConnectionFactory(connectionFactory);
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================================
    // fresh insert
    // ============================================================================================================

    @Test
    public void freshInsertPopulatesAllDocColumnsAndFlatRows() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-1");
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_modified", "2026-07-04T10:15:30Z")
                .set("_revision", 5)
                .set("attributes", JsonObject.newBuilder().set("a", 1).set("b", 2).set("c", 3).build())
                .build();
        final SearchIndexDocument document = documentBuilder(thingId, thing, 5L)
                .globalRead(List.of("subj-a", "subj-b"))
                .policyAuth(JsonObject.newBuilder()
                        .set("attributes", JsonObject.newBuilder().set("grant", "g").build()).build())
                .features(List.of(SearchIndexDocument.FeatureEntry.of("sensor",
                        JsonObject.newBuilder().set("properties",
                                JsonObject.newBuilder().set("temp", 21).build()).build(),
                        JsonObject.newBuilder().set("grant", "gf").build())))
                .build();

        final UpdaterResult result = run(ThingWriteModel.of(metadata(thingId, 5L), document));
        assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);

        assertThat(scalar("SELECT revision FROM search_things WHERE thing_id = '" + thingId + "'")).isEqualTo("5");
        assertThat(scalar("SELECT namespace FROM search_things WHERE thing_id = '" + thingId + "'"))
                .isEqualTo("org.eclipse.ditto");
        assertThat(scalar("SELECT policy_id FROM search_things WHERE thing_id = '" + thingId + "'"))
                .isEqualTo("org.eclipse.ditto:the-policy");
        assertThat(scalar("SELECT policy_rev FROM search_things WHERE thing_id = '" + thingId + "'")).isEqualTo("7");
        // referenced_policies mirrors Mongo __referencedPolicies: an array carrying the policy id (and revision).
        assertThat(scalar("SELECT referenced_policies->0->>'id' FROM search_things WHERE thing_id = '" + thingId + "'"))
                .isEqualTo("org.eclipse.ditto:the-policy");
        assertThat(scalar("SELECT referenced_policies->0->>'revision' FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("7");
        // global_read text[] carries both subjects.
        assertThat(scalar("SELECT array_to_string(global_read, ',') FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("subj-a,subj-b");
        // t_modified parsed from the thing _modified ISO string by the writer.
        assertThat(scalar("SELECT t_modified AT TIME ZONE 'UTC' FROM search_things WHERE thing_id = '"
                + thingId + "'")).startsWith("2026-07-04T10:15:30");
        // features_auth is a map feature-id -> auth tree.
        assertThat(scalar("SELECT features_auth->'sensor'->>'grant' FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("gf");
        // the thing JSONB round-trips.
        assertThat(scalar("SELECT thing->'attributes'->>'a' FROM search_things WHERE thing_id = '" + thingId + "'"))
                .isEqualTo("1");

        // flat rows == ThingFlattener output.
        assertThat(flatRowKeys(thingId)).isEqualTo(expectedFlatKeys(document));
    }

    // ============================================================================================================
    // update changing few leaves — v1.5 anti-join ctid stability
    // ============================================================================================================

    @Test
    public void updateChangingOneLeafKeepsUnchangedFlatRowCtidsAndReplacesChangedRow() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-ctid");
        run(ThingWriteModel.of(metadata(thingId, 1L), simpleAttributesDoc(thingId, 1L, 1, 2, 3)));

        final String ctidB = scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId
                + "' AND path = '/attributes/b'");
        final String ctidC = scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId
                + "' AND path = '/attributes/c'");
        final String ctidA = scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId
                + "' AND path = '/attributes/a'");

        // change only 'a' (1 -> 99); b and c stay identical.
        run(ThingWriteModel.of(metadata(thingId, 2L), simpleAttributesDoc(thingId, 2L, 99, 2, 3)));

        // anti-join proof: the unchanged rows were NOT rewritten -> same ctid.
        assertThat(scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId + "' AND path = '/attributes/b'"))
                .as("unchanged leaf b keeps its ctid").isEqualTo(ctidB);
        assertThat(scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId + "' AND path = '/attributes/c'"))
                .as("unchanged leaf c keeps its ctid").isEqualTo(ctidC);
        // the changed row was deleted+reinserted -> new value, new ctid.
        assertThat(scalar("SELECT val_num FROM search_flat WHERE thing_id = '" + thingId + "' AND path = '/attributes/a'"))
                .isEqualTo("99");
        assertThat(scalar("SELECT ctid FROM search_flat WHERE thing_id = '" + thingId + "' AND path = '/attributes/a'"))
                .as("changed leaf a is a fresh row").isNotEqualTo(ctidA);

        // final flat state matches a fresh flatten of the new document.
        assertThat(flatRowKeys(thingId)).isEqualTo(expectedFlatKeys(simpleAttributesDoc(thingId, 2L, 99, 2, 3)));
    }

    // ============================================================================================================
    // same-revision re-write with different auth data — round-2 Critical fan-out starvation regression
    // ============================================================================================================

    @Test
    public void sameRevisionRewriteWithDifferentAuthLands() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-fanout");
        run(ThingWriteModel.of(metadata(thingId, 9L),
                documentBuilder(thingId, minimalThing(thingId, 9L), 9L)
                        .globalRead(List.of("old-subject")).build()));
        assertThat(scalar("SELECT array_to_string(global_read, ',') FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("old-subject");

        // SAME revision (9), NEWER auth data (policy fan-out): the unconditional upsert MUST land.
        final UpdaterResult result = run(ThingWriteModel.of(metadata(thingId, 9L),
                documentBuilder(thingId, minimalThing(thingId, 9L), 9L)
                        .globalRead(List.of("new-subject-1", "new-subject-2")).build()));

        assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(scalar("SELECT array_to_string(global_read, ',') FROM search_things WHERE thing_id = '"
                + thingId + "'")).isEqualTo("new-subject-1,new-subject-2");
    }

    // ============================================================================================================
    // delete + delete of missing
    // ============================================================================================================

    @Test
    public void deleteCascadesFlatRowsAndDeletingMissingIsSuccess() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-del");
        run(ThingWriteModel.of(metadata(thingId, 1L), simpleAttributesDoc(thingId, 1L, 1, 2, 3)));
        assertThat(count("search_flat", thingId)).isGreaterThan(0);

        final UpdaterResult deleted = run(ThingDeleteModel.of(metadata(thingId, 2L)));
        assertThat(deleted.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
        assertThat(count("search_things", thingId)).isEqualTo(0);
        assertThat(count("search_flat", thingId)).as("FK cascade cleared flat rows").isEqualTo(0);

        // deleting a non-existent thing is still a success (Mongo delete semantics).
        final UpdaterResult deletedAgain = run(ThingDeleteModel.of(metadata(thingId, 3L)));
        assertThat(deletedAgain.result().classify()).isEqualTo(SearchWriteResultStatus.OK);
    }

    // ============================================================================================================
    // emptied-out tombstone
    // ============================================================================================================

    @Test
    public void emptiedOutWritesTombstoneDocRowAndZeroFlatRows() {
        final ThingId thingId = ThingId.of("org.eclipse.ditto", "thing-empty");
        run(ThingWriteModel.of(metadata(thingId, 1L), simpleAttributesDoc(thingId, 1L, 1, 2, 3)));
        assertThat(count("search_flat", thingId)).isGreaterThan(0);

        final UpdaterResult result = run(ThingWriteModel.ofEmptiedOut(metadata(thingId, 2L)));
        assertThat(result.result().classify()).isEqualTo(SearchWriteResultStatus.OK);

        // doc row remains (tombstone), referenced_policies is NULL (mirrors Mongo omitting __referencedPolicies).
        assertThat(count("search_things", thingId)).isEqualTo(1);
        assertThat(scalar("SELECT referenced_policies IS NULL FROM search_things WHERE thing_id = '" + thingId + "'"))
                .isEqualTo("true");
        // all flat rows gone.
        assertThat(count("search_flat", thingId)).isEqualTo(0);
    }

    // ============================================================================================================
    // helpers
    // ============================================================================================================

    private UpdaterResult run(final AbstractWriteModel model) {
        final UpdaterData data = new UpdaterData(model, model);
        try {
            return Source.single(data)
                    .via(flow.create())
                    .runWith(Sink.head(), system)
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("flow failed", e);
        }
    }

    private static Metadata metadata(final ThingId thingId, final long revision) {
        final PolicyTag policyTag = PolicyTag.of(PolicyId.of("org.eclipse.ditto", "the-policy"), 7L);
        return Metadata.of(thingId, revision, policyTag, null, Set.of(policyTag), null);
    }

    private static SearchIndexDocument.Builder documentBuilder(final ThingId thingId, final JsonObject thing,
            final long revision) {
        return SearchIndexDocument.newBuilder(thingId)
                .namespace(thingId.getNamespace())
                .revision(revision)
                .policyId(PolicyId.of("org.eclipse.ditto", "the-policy"))
                .policyRevision(7L)
                .referencedPolicies(Set.of(PolicyTag.of(PolicyId.of("org.eclipse.ditto", "the-policy"), 7L)))
                .thing(thing);
    }

    private static SearchIndexDocument simpleAttributesDoc(final ThingId thingId, final long revision,
            final int a, final int b, final int c) {
        final JsonObject thing = JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", (int) revision)
                .set("attributes", JsonObject.newBuilder().set("a", a).set("b", b).set("c", c).build())
                .build();
        return documentBuilder(thingId, thing, revision).build();
    }

    private static JsonObject minimalThing(final ThingId thingId, final long revision) {
        return JsonObject.newBuilder()
                .set("thingId", thingId.toString())
                .set("_namespace", thingId.getNamespace())
                .set("_revision", (int) revision)
                .build();
    }

    /** Canonical key of a flat row for order-insensitive set comparison. */
    private static String flatKey(final String path, final String wpath, final String fId, final int ord,
            final int typeRank, final Boolean valBool, final String valNum, final String valText) {
        return path + "|" + wpath + "|" + fId + "|" + ord + "|" + typeRank + "|" + valBool + "|"
                + (valNum == null ? "null" : new BigDecimal(valNum).stripTrailingZeros().toPlainString())
                + "|" + valText;
    }

    private static Set<String> expectedFlatKeys(final SearchIndexDocument document) {
        return ThingFlattener.flatten(document).stream()
                .map(PostgresSearchWritePathIT::flatKey)
                .collect(Collectors.toSet());
    }

    private static String flatKey(final FlatRow row) {
        return flatKey(row.path(), row.wpath(), row.fId(), row.ord(), row.typeRank(), row.valBool(),
                row.valNum() == null ? null : row.valNum().toPlainString(), row.valText());
    }

    private Set<String> flatRowKeys(final ThingId thingId) {
        return query("SELECT path, wpath, f_id, ord, type_rank, val_bool, val_num::text AS val_num, val_text "
                        + "FROM search_flat WHERE thing_id = '" + thingId + "'",
                (row, meta) -> flatKey(
                        row.get("path", String.class),
                        row.get("wpath", String.class),
                        row.get("f_id", String.class),
                        row.get("ord", Integer.class),
                        row.get("type_rank", Short.class).intValue(),
                        row.get("val_bool", Boolean.class),
                        row.get("val_num", String.class),
                        row.get("val_text", String.class)))
                .stream().collect(Collectors.toSet());
    }

    private int count(final String table, final ThingId thingId) {
        return Integer.parseInt(scalar("SELECT count(*) FROM " + table + " WHERE thing_id = '" + thingId + "'"));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static <T> List<T> query(final String sql, final BiFunction<Row, RowMetadata, T> mapper) {
        final List<T> out = new ArrayList<>();
        Flux.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map(mapper)),
                        Connection::close)
                .doOnNext(out::add)
                .blockLast();
        return out;
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
