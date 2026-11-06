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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.internal.utils.persistence.api.DeleteOutcome;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PostgresMetrics;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PostgresMetricsRecorder;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.R2dbcMetricsListener;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.proxy.ProxyConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcTimeoutException;
import io.r2dbc.spi.Result;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.AtomicWrite;
import org.apache.pekko.persistence.PersistentRepr;
import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;
import org.apache.pekko.persistence.journal.Tagged;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Testcontainers parity matrix against a real PostgreSQL (PG 16) — the scenarios not already
 * covered by {@link PostgresPersistenceIT} (which holds the high-water-mark, priority-ordering, snapshot-criteria and
 * idempotency happy paths) and
 * {@link org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManagerIT} (bootstrap idempotency
 * and the divergent-primary-key refuse-to-boot case).
 * <p>
 * Each test owns its own pids so they are order-independent against one shared, bootstrapped database. Docker
 * unreachability turns into a JUnit skip via the {@link #startContainer() Assume} guard, not a failure.
 * </p>
 * <p>
 * Covered here: the GIN-index EXPLAIN hard gate plus strict descending priority ordering; recovery of a stored payload
 * straight into a {@code JsonObject} (historical-revision recovery and a JSONB snapshot decoded to a {@code JsonObject}
 * with no intermediate BSON value); duplicate writes that are fatal on a genuine payload mismatch and idempotent on a
 * snapshot retry; the read-journal {@code deleteEvents}/{@code deleteSnapshots} bounded range; oversize JSONB / TOAST
 * payloads; partial-batch rollback; checksum-mismatch refuse-to-boot; an N=2 concurrent first-boot DDL race; a pool
 * acquire-timeout that fails the write rather than applying backpressure; the neutral observability metric tag-keys and
 * op-kind domain; and the {@code getNewestSnapshotsAbove} result shape.
 * </p>
 */
public final class PostgresParityMatrixIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ActorSystem system;
    private static Materializer mat;
    private static ConnectionFactory ddlFactory;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;
    private static PostgresJournalOps journal;
    private static PostgresSnapshotStoreOps snapshots;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresParityMatrixIT", t);
        }
        ddlFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresParityMatrixIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("matrix-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
        journal = PostgresJournalOps.of(operations);
        snapshots = PostgresSnapshotStoreOps.of(operations);
        readJournal = PostgresReadJournal.of(operations);
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        if (system != null) {
            system.terminate();
        }
        POSTGRES.stop();
    }

    // =================================================================================================
    // GIN index usage — HARD GATE: EXPLAIN must NOT show a sequential scan for `tags @> ARRAY[...]`.
    // =================================================================================================

    @Test
    public void tagContainmentUsesGinIndexNotSeqScan() throws Exception {
        // Populate enough rows that the planner would prefer the GIN index over a seq scan.
        for (int i = 0; i < 200; i++) {
            await(journal.writeMessages(List.of(write("thing:ns:gin-" + i, 1L, "{\"i\":" + i + "}",
                    "always-alive", "priority-" + (i % 7)))));
        }
        runDdl("ANALYZE things_journal");

        // Hard gate on index USABILITY for the `@>` containment operator. On a small table the planner legitimately
        // prefers a seq scan on cost, so we disable seq scans for this one session: if (and only if) the GIN index is
        // usable for `tags @> ARRAY[...]` will the planner then reveal a Bitmap Index Scan on the GIN index. The silent
        // `= ANY(tags)` trap would have NO index path even with seqscan off, so this assertion catches it.
        final String plan = explainWithoutSeqScan(
                "SELECT pid FROM things_journal WHERE tags @> ARRAY['always-alive']::text[]");
        assertThat(plan.toLowerCase()).contains("things_journal_tags_idx");
        assertThat(plan.toLowerCase()).contains("bitmap index scan");
    }

    // =================================================================================================
    // strict descending priority ordering with DISTINCT priorities (containsExactly is well-defined).
    // =================================================================================================

    @Test
    public void priorityOrderingIsStrictlyDescendingWithDistinctPriorities() throws Exception {
        final String p1 = "thing:ord:p1";
        final String p2 = "thing:ord:p2";
        final String p3 = "thing:ord:p3";
        final String p4 = "thing:ord:p4";
        final String p5 = "thing:ord:p5"; // always-alive, NO priority tag -> default 0, must be PRESENT.
        await(journal.writeMessages(List.of(write(p1, 1L, "{}", "ord", "priority-10"))));
        await(journal.writeMessages(List.of(write(p2, 1L, "{}", "ord", "priority-2"))));
        await(journal.writeMessages(List.of(write(p3, 1L, "{}", "ord", "priority-3"))));
        await(journal.writeMessages(List.of(write(p4, 1L, "{}", "ord", "priority-4"))));
        await(journal.writeMessages(List.of(write(p5, 1L, "{}", "ord"))));

        final List<String> ordered = readJournal.getJournalPidsWithTagOrderedByPriorityTag("ord", Duration.ZERO)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);

        // The four distinct-priority pids are strictly ordered 10 > 4 > 3 > 2 (highest first), matching Mongo.
        final List<String> distinctPriorityPids =
                ordered.stream().filter(pid -> !pid.equals(p5)).toList();
        assertThat(distinctPriorityPids).containsExactly(p1, p4, p3, p2);
        // The no-priority pid is kept (default 0), present in any position.
        assertThat(ordered).contains(p5);
    }

    // =================================================================================================
    // historical-revision recovery round-trips a real event straight into a JsonObject.
    // a JSONB snapshot decodes to a JsonObject with no intermediate BSON value.
    // =================================================================================================

    @Test
    public void historicalRevisionRecoveryRoundTripsRealEventViaJsonObject() throws Exception {
        final String pid = "thing:c2:revrec";
        final JsonObject event = JsonFactory.newObject("{\"thingId\":\"" + pid + "\",\"rev\":7,\"nested\":{\"a\":[1,2]}}");
        await(journal.writeMessages(List.of(write(pid, 7L, event.toString()))));

        final List<EventEnvelope> envelopes = readJournal.currentEventsByPersistenceId(pid, 0L, Long.MAX_VALUE)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(envelopes).hasSize(1);

        // the backend-agnostic toEventJson must turn the JSONB-derived payload into a JsonObject without any
        // intermediate BSON value (a BSON cast would throw ClassCastException under Postgres).
        final JsonObject recovered = readJournal.toEventJson(envelopes.get(0));
        assertThat(recovered).isEqualTo(event);
    }

    @Test
    public void jsonbSnapshotDecodesToJsonObjectWithoutBsonValueCast() throws Exception {
        final String pid = "thing:g1:snap";
        final JsonObject snapshot = JsonFactory.newObject(
                "{\"thingId\":\"" + pid + "\",\"__lifecycle\":\"ACTIVE\",\"value\":42}");
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 3L, 5_000L), snapshot.toString()));

        final Optional<SelectedSnapshot> loaded =
                await(snapshots.loadAsync(pid, SnapshotSelectionCriteria.latest()));
        assertThat(loaded).isPresent();
        // the loaded snapshot payload is the raw JSON text (NOT a BSON value); it decodes to the same JsonObject.
        final Object payload = loaded.get().snapshot();
        final JsonObject decoded = JsonFactory.newObject(String.valueOf(payload));
        assertThat(decoded).isEqualTo(snapshot);
    }

    // =================================================================================================
    // duplicate (pid,sn) with a DIFFERENT payload is fatal; same payload is idempotent (covered in
    // PostgresPersistenceIT). Snapshot retry idempotency (same metadata -> upsert, no crash).
    // =================================================================================================

    @Test
    public void duplicateWriteWithDifferentPayloadIsFatal() throws Exception {
        final String pid = "thing:h4:mismatch";
        await(journal.writeMessages(List.of(write(pid, 1L, "{\"v\":1}"))));

        // A divergent-payload duplicate at the same (pid,sn) is FATAL — the write Future FAILS
        // (-> Pekko WriteMessageFailure, the persistent actor stops). It must NOT complete with a
        // recoverable per-message rejection, which would silently drop the divergent write.
        assertThatThrownBy(() -> await(journal.writeMessages(List.of(write(pid, 1L, "{\"v\":999}")))))
                .isInstanceOf(ExecutionException.class)
                .hasMessageContaining("DIFFERENT payload");
    }

    @Test
    public void snapshotRetryWithSameMetadataIsIdempotentUpsert() throws Exception {
        final String pid = "thing:h4:snapretry";
        final SnapshotMetadata metadata = new SnapshotMetadata(pid, 4L, 7_000L);
        await(snapshots.saveAsync(metadata, "{\"v\":\"first\"}"));
        // Same (pid, sn, written_at) -> ON CONFLICT DO UPDATE; must not crash, must overwrite.
        await(snapshots.saveAsync(metadata, "{\"v\":\"second\"}"));

        final Optional<SelectedSnapshot> loaded =
                await(snapshots.loadAsync(pid, SnapshotSelectionCriteria.latest()));
        assertThat(loaded).isPresent();
        assertThat(JsonFactory.newObject(String.valueOf(loaded.get().snapshot())))
                .isEqualTo(JsonFactory.newObject("{\"v\":\"second\"}"));
        // exactly one row remains (idempotent, not a second insert).
        assertThat(rowCount("things_snaps", pid)).isEqualTo(1L);
    }

    // =================================================================================================
    // read-journal bounded PK-range deleteEvents / deleteSnapshots (distinct from SnapshotStore.deleteAsync).
    // =================================================================================================

    @Test
    public void readJournalDeleteEventsAndSnapshotsAreBoundedRanges() throws Exception {
        final String pid = "thing:h9:rjdelete";
        for (long sn = 1; sn <= 5; sn++) {
            await(journal.writeMessages(List.of(write(pid, sn, "{\"sn\":" + sn + "}"))));
            await(snapshots.saveAsync(new SnapshotMetadata(pid, sn, sn * 1000L), "{\"sn\":" + sn + "}"));
        }

        // delete sn in [2,4] only -> rows 1 and 5 survive on both tables.
        final DeleteOutcome eventsDeleted = readJournal.deleteEvents(pid, 2L, 4L)
                .runWith(Sink.head(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        final DeleteOutcome snapsDeleted = readJournal.deleteSnapshots(pid, 2L, 4L)
                .runWith(Sink.head(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(eventsDeleted.isAcknowledged()).isTrue();
        assertThat(snapsDeleted.isAcknowledged()).isTrue();

        final List<Long> remainingEvents = new ArrayList<>();
        await(journal.replayMessages(pid, 0L, Long.MAX_VALUE, Long.MAX_VALUE,
                repr -> remainingEvents.add(repr.sequenceNr())));
        assertThat(remainingEvents).containsExactly(1L, 5L);
        assertThat(rowCount("things_snaps", pid)).isEqualTo(2L);
    }

    // =================================================================================================
    // getNewestSnapshotsAbove — newest snapshot per pid in pid order (cleanup stream shape).
    // =================================================================================================

    @Test
    public void getNewestSnapshotsAbovePicksNewestPerPid() throws Exception {
        final String pid = "thing:cleanup:newest";
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 1L, 1L), "{\"v\":1}"));
        await(snapshots.saveAsync(new SnapshotMetadata(pid, 9L, 9L), "{\"v\":9}"));

        final List<SnapshotEntry> entries =
                readJournal.getNewestSnapshotsAbove("thing:cleanup:", 100, false, Duration.ZERO, mat)
                        .filter(e -> e.getPid().filter(pid::equals).isPresent())
                        .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSequenceNumber().getAsLong()).isEqualTo(9L);
    }

    // =================================================================================================
    // getNewestSnapshotsAbove — DELETED-lifecycle exclusion. The `__lifecycle` marker is lifted into the
    // `lifecycle` column on save; includeDeleted=false drops a pid whose newest snapshot is DELETED (filtered in
    // `PostgresReadJournal` after the newest-per-pid pick; no SQL filters on `lifecycle`), so a
    // tombstoned snapshot is hidden from cleanup unless includeDeleted=true. ageWindow is Duration.ZERO so
    // the freshly-seeded rows (written_at ≈ now()) are not excluded by the `written_at < now() - $::interval`
    // age predicate.
    // =================================================================================================

    @Test
    public void getNewestSnapshotsAboveExcludesDeletedUnlessIncludeDeleted() throws Exception {
        final String activePid = "thing:lifecycle:active";
        final String deletedPid = "thing:lifecycle:deleted";
        await(snapshots.saveAsync(new SnapshotMetadata(activePid, 1L, 1L),
                "{\"thingId\":\"" + activePid + "\",\"__lifecycle\":\"ACTIVE\",\"v\":1}"));
        await(snapshots.saveAsync(new SnapshotMetadata(deletedPid, 1L, 1L),
                "{\"thingId\":\"" + deletedPid + "\",\"__lifecycle\":\"DELETED\"}"));

        // includeDeleted=false -> only the ACTIVE snapshot surfaces; the DELETED tombstone is filtered out.
        final List<String> active = newestPidsInRange("thing:lifecycle:", false, activePid, deletedPid);
        assertThat(active).containsExactly(activePid);

        // includeDeleted=true -> both the ACTIVE snapshot and the DELETED tombstone surface (pid order).
        final List<String> both = newestPidsInRange("thing:lifecycle:", true, activePid, deletedPid);
        assertThat(both).containsExactlyInAnyOrder(activePid, deletedPid);
    }

    /** Newest-per-pid pids in the given prefix range, restricted to the two seeded pids, ordered by pid. */
    private static List<String> newestPidsInRange(final String lowerBoundPid, final boolean includeDeleted,
            final String... seededPids) throws Exception {
        final List<String> seeded = List.of(seededPids);
        return readJournal.getNewestSnapshotsAbove(lowerBoundPid, 100, includeDeleted, Duration.ZERO, mat)
                .map(e -> e.getPid().orElse(null))
                .filter(seeded::contains)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    // =================================================================================================
    // Oversize JSONB / TOAST payload round-trips (a >1MB value forces out-of-line TOAST storage).
    // =================================================================================================

    @Test
    public void oversizeToastPayloadRoundTrips() throws Exception {
        final String pid = "thing:toast:big";
        final String big = "x".repeat(2_000_000); // ~2 MB -> exceeds the ~8 KB TOAST threshold, stored out-of-line.
        final JsonObject event = JsonFactory.newObject().toBuilder().set("blob", big).build();
        await(journal.writeMessages(List.of(write(pid, 1L, event.toString()))));

        final List<EventEnvelope> envelopes = readJournal.currentEventsByPersistenceId(pid, 0L, Long.MAX_VALUE)
                .runWith(Sink.seq(), mat).toCompletableFuture().get(30, TimeUnit.SECONDS);
        assertThat(envelopes).hasSize(1);
        final JsonObject recovered = readJournal.toEventJson(envelopes.get(0));
        assertThat(recovered.getValue("blob").map(org.eclipse.ditto.json.JsonValue::asString)
                .orElse("").length()).isEqualTo(2_000_000);
    }

    // =================================================================================================
    // Partial-batch rollback: a transactional batch where one row violates a constraint must roll back
    // ALL rows of that transaction (atomicity of the per-statement-group write path).
    // =================================================================================================

    @Test
    public void invalidJsonbInTransactionRollsBackEntirely() throws Exception {
        final String pid = "thing:rollback:tx";
        // Bind a malformed JSON value that pg will reject at INSERT ... ::jsonb time. Drive it directly so the failing
        // statement shares a transaction with a preceding valid statement, proving the whole tx rolls back.
        final Long rolledBack = Mono.usingWhen(
                        Mono.from(client.getConnectionPool().create()),
                        conn -> Mono.from(conn.beginTransaction())
                                .then(exec(conn, "INSERT INTO things_journal (pid, sn, manifest, tags, event) "
                                        + "VALUES ('" + pid + "', 1, 'm', '{}', '{\"ok\":true}'::jsonb)"))
                                .then(exec(conn, "INSERT INTO things_journal (pid, sn, manifest, tags, event) "
                                        + "VALUES ('" + pid + "', 2, 'm', '{}', 'NOT_JSON'::jsonb)"))
                                .then(Mono.from(conn.commitTransaction()))
                                .thenReturn(0L)
                                .onErrorResume(err -> Mono.from(conn.rollbackTransaction()).then(Mono.error(err))),
                        Connection::close)
                .onErrorReturn(-1L)
                .block(Duration.ofSeconds(20));
        assertThat(rolledBack).isEqualTo(-1L); // the tx errored and rolled back.

        // Neither row of the rolled-back transaction is present.
        assertThat(rowCount("things_journal", pid)).isEqualTo(0L);
    }

    // =================================================================================================
    // checksum-mismatch refuse-to-boot (distinct from the divergent-PK case in the schema-manager IT).
    // =================================================================================================

    @Test
    public void checksumMismatchRefusesToBoot() {
        // Poison the stored checksum, then a bootstrap must refuse to boot on the code-vs-stored mismatch.
        runDdl("INSERT INTO schema_version (component, version, checksum) "
                + "VALUES ('ditto-postgres-persistence', 1, 'POISONED-CHECKSUM') "
                + "ON CONFLICT (component) DO UPDATE SET checksum = 'POISONED-CHECKSUM'");
        try {
            assertThatThrownBy(() -> PostgresSchemaManager.of(POSTGRES.newConnectionFactory(), PostgresSchema.descriptor()).bootstrap())
                    .isInstanceOf(org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException.class)
                    .hasMessageContaining("checksum");
        } finally {
            // restore the correct checksum so later runs / re-bootstraps are clean.
            runDdl("UPDATE schema_version SET checksum = '"
                    + PostgresSchemaManager.checksum(PostgresSchema.descriptor())
                    + "' WHERE component = 'ditto-postgres-persistence'");
        }
    }

    // =================================================================================================
    // N=2 concurrent first-boot DDL race: two bootstraps against a fresh DB both succeed (advisory xact
    // lock serialises; IF NOT EXISTS makes the loser a no-op).
    // =================================================================================================

    @Test
    public void concurrentFirstBootDdlRaceBothSucceed() throws Exception {
        // Use a SEPARATE fresh database so this race starts from zero tables, independent of the shared schema.
        final String raceDb = "race_db";
        runDdl("DROP DATABASE IF EXISTS " + raceDb);
        runDdl("CREATE DATABASE " + raceDb);
        try {
            final CountDownLatch ready = new CountDownLatch(2);
            final CountDownLatch go = new CountDownLatch(1);
            final List<AtomicReference<Throwable>> errors = List.of(new AtomicReference<>(), new AtomicReference<>());
            final List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                final Thread t = new Thread(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        PostgresSchemaManager.of(raceFactory(raceDb), PostgresSchema.descriptor()).bootstrap();
                    } catch (final Throwable e) {
                        errors.get(idx).set(e);
                    }
                });
                threads.add(t);
                t.start();
            }
            ready.await(20, TimeUnit.SECONDS);
            go.countDown(); // release both bootstraps as simultaneously as possible.
            for (final Thread t : threads) {
                t.join(60_000L);
            }
            assertThat(errors.get(0).get()).withFailMessage("bootstrap 0 failed: %s", errors.get(0).get()).isNull();
            assertThat(errors.get(1).get()).withFailMessage("bootstrap 1 failed: %s", errors.get(1).get()).isNull();
            // both succeeded and the schema exists in the race DB.
            assertThat(scalarOn(raceDb,
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'things_journal')"))
                    .isEqualTo("true");
        } finally {
            runDdl("DROP DATABASE IF EXISTS " + raceDb);
        }
    }

    // =================================================================================================
    // pool acquire-timeout fails the write Future (a LOST WRITE that stops the actor) — NOT backpressure.
    // =================================================================================================

    @Test
    public void poolAcquireTimeoutFailsTheWrite() throws Exception {
        // A pool of size 1 with a tiny acquire-timeout: hold the only connection, then a concurrent write must fail
        // its Future with an acquire timeout (which the journal maps to a failed Future == lost write == actor stop).
        final ConnectionPool tinyPool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                .name("h12-pool").initialSize(1).maxSize(1).maxAcquireTime(Duration.ofMillis(250L)).build());
        final DittoPostgresClient tinyClient =
                DittoPostgresClient.forConnectionPool(tinyPool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresJournalOps tinyJournal =
                PostgresJournalOps.of(PostgresPersistenceOperations.of(tinyClient, "things"));
        // Grab and HOLD the sole connection so no other acquire can succeed within the timeout.
        final Connection held = Mono.from(tinyPool.create()).block(Duration.ofSeconds(10));
        try {
            final CompletionStage<Iterable<Optional<Exception>>> writeStage =
                    tinyJournal.writeMessages(List.of(write("thing:h12:lost", 1L, "{\"x\":1}")));
            assertThatThrownBy(() -> writeStage.toCompletableFuture().get(15, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(R2dbcTimeoutException.class);
        } finally {
            if (held != null) {
                Mono.from(held.close()).block(Duration.ofSeconds(10));
            }
            tinyClient.close();
        }
    }

    // =================================================================================================
    // observability: a real query through the r2dbc-proxy listener emits a command with the expected
    // tag keys (engine/op_kind/status) and an op-kind from the neutral domain.
    // =================================================================================================

    @Test
    public void observabilityEmitsNeutralCommandMetric() throws Exception {
        final List<String[]> captured = new ArrayList<>();
        final PostgresMetricsRecorder recorder = new PostgresMetricsRecorder() {
            @Override
            public void recordCommand(final String opKind, final String status, final long nanos) {
                synchronized (captured) {
                    captured.add(new String[] {opKind, status});
                }
            }

            @Override
            public void recordAcquire(final long nanos) {
                // not asserted here.
            }
        };
        final ConnectionFactory proxied = ProxyConnectionFactory.builder(ddlFactory)
                .listener(R2dbcMetricsListener.of(recorder))
                .build();
        // Run a real SELECT through the proxied factory.
        Mono.usingWhen(Mono.from(proxied.create()),
                        conn -> Flux.from(conn.createStatement("SELECT 1").execute())
                                .flatMap(r -> r.map((row, meta) -> row.get(0, Integer.class)))
                                .then(),
                        Connection::close)
                .block(Duration.ofSeconds(20));

        synchronized (captured) {
            assertThat(captured).isNotEmpty();
            final String[] sample = captured.get(0);
            // op-kind is from the closed neutral domain; status is success for a healthy SELECT.
            assertThat(PostgresMetrics.OP_KIND_DOMAIN).contains(sample[0]);
            assertThat(PostgresMetrics.STATUS_DOMAIN).contains(sample[1]);
            assertThat(sample[0]).isEqualTo(PostgresMetrics.OP_SELECT);
            assertThat(sample[1]).isEqualTo(PostgresMetrics.STATUS_SUCCESS);
        }
        // tag KEYS are the engine/op_kind/status keys (no _mongodb suffix, no cross-engine parity).
        assertThat(PostgresMetrics.TAG_ENGINE).isEqualTo("engine");
        assertThat(PostgresMetrics.TAG_OP_KIND).isEqualTo("op_kind");
        assertThat(PostgresMetrics.TAG_STATUS).isEqualTo("status");
        assertThat(PostgresMetrics.COMMAND_DURATION).isEqualTo("ditto_persistence_command_duration");
    }

    // =================================================================================================
    // helpers
    // =================================================================================================

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

    private static AtomicWrite write(final String pid, final long sn, final String json, final String... tags) {
        final Object payload = tags.length == 0
                ? (Object) JsonFactory.newObject(json)
                : new Tagged(JsonFactory.newObject(json),
                        CollectionConverters.asScala(java.util.Arrays.asList(tags)).toSet());
        return AtomicWrite.apply(PersistentRepr.apply(payload, sn, pid, "manifest", false, ActorRef.noSender(), "w"));
    }

    private static Mono<Void> exec(final Connection conn, final String sql) {
        return Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then();
    }

    private static String explainWithoutSeqScan(final String sql) {
        final StringBuilder plan = new StringBuilder();
        Flux.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement("SET enable_seqscan = off").execute())
                                .flatMap(Result::getRowsUpdated)
                                .thenMany(Flux.from(conn.createStatement("EXPLAIN " + sql).execute())
                                        .flatMap(r -> r.map((row, meta) -> String.valueOf(row.get(0))))),
                        Connection::close)
                .doOnNext(line -> plan.append(line).append('\n'))
                .blockLast(Duration.ofSeconds(20));
        return plan.toString();
    }

    private static long rowCount(final String table, final String pid) {
        final Long count = Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                        "SELECT count(*) AS c FROM " + table + " WHERE pid = '" + pid + "'").execute())
                                .flatMap(r -> r.map((row, meta) -> row.get("c", Long.class)))
                                .next(),
                        Connection::close)
                .block(Duration.ofSeconds(20));
        return count == null ? 0L : count;
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then(),
                        Connection::close)
                .block(Duration.ofSeconds(30));
    }

    private static ConnectionFactory raceFactory(final String database) {
        return io.r2dbc.spi.ConnectionFactories.get(io.r2dbc.spi.ConnectionFactoryOptions.builder()
                .option(io.r2dbc.spi.ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(io.r2dbc.spi.ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(io.r2dbc.spi.ConnectionFactoryOptions.PORT, POSTGRES.getPort())
                .option(io.r2dbc.spi.ConnectionFactoryOptions.DATABASE, database)
                .option(io.r2dbc.spi.ConnectionFactoryOptions.USER, PostgresDbResource.DDL_USER)
                .option(io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD, PostgresDbResource.DDL_PASSWORD)
                .build());
    }

    private static String scalarOn(final String database, final String sql) {
        final ConnectionFactory cf = raceFactory(database);
        return Mono.usingWhen(Mono.from(cf.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(r -> r.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block(Duration.ofSeconds(20));
    }

}
