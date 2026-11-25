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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Statement;

/**
 * Contract tests of {@link PostgresPersistedPayloadStore} on a real PostgreSQL (PG 16) via Testcontainers, seeded with
 * SQL: the keyset order and page boundaries of both streams, resuming behind a cursor, the required-field filter
 * (present, JSON null, missing, nested, pointer through a non-object), rewrites that address exactly one row and roll
 * back as a whole, the untouched row of an unchanged journal entry, the progress record's life cycle and the no-op
 * {@code close()}. Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 */
public final class PostgresPersistedPayloadStoreIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final List<String> SNAPSHOT_POINTERS = List.of("/uri", "/credentials/password");
    private static final List<String> JOURNAL_POINTERS = List.of("/connection/uri", "/connection/credentials/password");
    private static final Instant T0 = Instant.parse("2026-11-06T10:00:00.000001Z");

    private static ActorSystem system;
    private static Materializer mat;
    private static DittoPostgresClient client;
    private static PostgresPersistedPayloadStore underTest;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPersistedPayloadStoreIT",
                    t);
        }
        final ConnectionFactory factory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresPersistedPayloadStoreIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("payload-store-it-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        underTest = PostgresPersistedPayloadStore.of(client, "connections");
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

    @Before
    public void truncate() throws Exception {
        execute("TRUNCATE connections_journal, connections_journal_seq, connections_snaps, "
                + PostgresSchema.ENCRYPTION_MIGRATION_TABLE + " RESTART IDENTITY", null);
    }

    // --- snapshots ---

    @Test
    public void snapshotsStreamInPrimaryKeyOrderAcrossPagesWithoutLossOrRepetition() throws Exception {
        // five rows over three pids, one pid with two sns and one (pid, sn) with two written_at; inserted out of order
        insertSnapshot("connection:b", 1L, T0, secret("b1"));
        insertSnapshot("connection:a", 2L, T0.plusSeconds(5), secret("a2"));
        insertSnapshot("connection:a", 1L, T0.plusSeconds(9), secret("a1-later"));
        insertSnapshot("connection:a", 1L, T0, secret("a1"));
        insertSnapshot("connection:c", 1L, T0, secret("c1"));

        final List<PersistedSnapshot> streamed = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 2)
                .runWith(Sink.seq(), mat));

        assertThat(streamed).extracting(s -> s.payload().getValue("/name").orElseThrow().asString())
                .containsExactly("a1", "a1-later", "a2", "b1", "c1");
        assertThat(streamed).extracting(PersistedSnapshot::cursor).doesNotHaveDuplicates();
        // the cursor is the row's key and parses back exactly
        assertThat(SnapshotKey.fromCursor(streamed.get(1).cursor()))
                .isEqualTo(new SnapshotKey("connection:a", 1L, T0.plusSeconds(9)));

        // one row per page: every page boundary, including the one between the two rows of (a, 1), keeps the order
        final List<PersistedSnapshot> onePerPage = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 1)
                .runWith(Sink.seq(), mat));
        assertThat(onePerPage).extracting(PersistedSnapshot::cursor)
                .containsExactlyElementsOf(streamed.stream().map(PersistedSnapshot::cursor).toList());
    }

    @Test
    public void snapshotsResumeStrictlyBehindTheCursor() throws Exception {
        insertSnapshot("connection:a", 1L, T0, secret("a1"));
        insertSnapshot("connection:a", 1L, T0.plusSeconds(1), secret("a1-later"));
        insertSnapshot("connection:b", 1L, T0, secret("b1"));
        insertSnapshot("connection:c", 1L, T0, secret("c1"));
        final List<PersistedSnapshot> all = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat));

        final List<PersistedSnapshot> resumed = await(underTest.streamSnapshots(all.get(1).cursor(),
                SNAPSHOT_POINTERS, 1).runWith(Sink.seq(), mat));

        assertThat(resumed).extracting(PersistedSnapshot::pid).containsExactly("connection:b", "connection:c");
        // behind the first of the two rows of (a, 1): the later-written_at row of the same (pid, sn) comes first
        assertThat(await(underTest.streamSnapshots(all.get(0).cursor(), SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat)))
                .extracting(s -> s.payload().getValue("/name").orElseThrow().asString())
                .containsExactly("a1-later", "b1", "c1");
        assertThat(await(underTest.streamSnapshots(all.get(3).cursor(), SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat))).as("behind the last row: nothing").isEmpty();
    }

    @Test
    public void requiredFieldFilterMatchesPresentAndNullFieldsButNotMissingOrNonObjectPaths() throws Exception {
        insertSnapshot("connection:present", 1L, T0, "{\"uri\":\"amqps://u:p@h\",\"name\":\"present\"}");
        insertSnapshot("connection:null", 1L, T0, "{\"uri\":null,\"name\":\"null\"}");
        insertSnapshot("connection:missing", 1L, T0, "{\"name\":\"missing\"}");
        insertSnapshot("connection:nested", 1L, T0, "{\"credentials\":{\"password\":\"p\"},\"name\":\"nested\"}");
        insertSnapshot("connection:nonobject", 1L, T0, "{\"credentials\":\"a string\",\"name\":\"nonobject\"}");
        insertSnapshot("connection:other-key", 1L, T0, "{\"credentials\":{\"username\":\"u\"},\"name\":\"other\"}");

        final List<PersistedSnapshot> streamed = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat));

        assertThat(streamed).extracting(PersistedSnapshot::pid)
                .containsExactly("connection:nested", "connection:null", "connection:present");
    }

    @Test
    public void rewriteSnapshotsChangesExactlyTheAddressedRowAndReturnsTheCount() throws Exception {
        // two snapshots of one pid and sn that differ only in written_at are told apart
        insertSnapshot("connection:a", 1L, T0, secret("a-old"));
        insertSnapshot("connection:a", 1L, T0.plusSeconds(1), secret("a-new"));
        insertSnapshot("connection:b", 1L, T0, secret("b"));
        final List<PersistedSnapshot> streamed = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat));
        final PersistedSnapshot newer = streamed.get(1);
        assertThat(newer.payload().getValue("/name").orElseThrow().asString()).isEqualTo("a-new");

        final Long written = await(underTest.rewriteSnapshots(List.of(
                newer.withPayload(newer.payload().setValue("/uri", "amqps://u:encrypted_x@h")))));

        assertThat(written).isEqualTo(1L);
        assertThat(snapshotText("connection:a", 1L, T0)).contains("\"name\": \"a-old\"").doesNotContain("encrypted_");
        assertThat(snapshotText("connection:a", 1L, T0.plusSeconds(1))).contains("encrypted_x");
        assertThat(snapshotText("connection:b", 1L, T0)).doesNotContain("encrypted_");
    }

    @Test
    public void rewriteSnapshotsOfABatchIsAtomic() throws Exception {
        insertSnapshot("connection:a", 1L, T0, secret("a"));
        insertSnapshot("connection:b", 1L, T0, secret("b"));
        final List<PersistedSnapshot> streamed = await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat));
        final PersistedSnapshot valid = streamed.get(0)
                .withPayload(streamed.get(0).payload().setValue("/uri", "amqps://u:encrypted_x@h"));
        // the escaped NUL Ditto renders for a U+0000 in a string is rejected by PostgreSQL jsonb (SQLSTATE 22P05)
        final PersistedSnapshot rejected = streamed.get(1)
                .withPayload(streamed.get(1).payload().setValue("/uri", "a\u0000b"));

        final Throwable failure = failureOf(underTest.rewriteSnapshots(List.of(valid, rejected)));

        assertThat(failure).isInstanceOf(R2dbcException.class);
        assertThat(((R2dbcException) failure).getSqlState()).startsWith("22");
        assertThat(snapshotText("connection:a", 1L, T0)).as("the whole batch rolled back").doesNotContain("encrypted_");
        assertThat(snapshotText("connection:b", 1L, T0)).doesNotContain("u0000");
    }

    // --- journal ---

    @Test
    public void journalEntriesStreamBySeqAcrossPagesAndResumeBehindTheCursor() throws Exception {
        insertEvent("connection:b", 1L, journalSecret("b1"));
        insertEvent("connection:a", 1L, journalSecret("a1"));
        insertEvent("connection:a", 2L, "{\"type\":\"connectivity.events:connectionDeleted\"}"); // no secret field
        insertEvent("connection:c", 1L, journalSecret("c1"));
        insertEvent("connection:a", 3L, journalSecret("a3"));

        final List<PersistedJournalEntry> streamed = await(underTest.streamJournalEntries(null, JOURNAL_POINTERS, 2)
                .runWith(Sink.seq(), mat));

        // seq order = insertion order; the secret-less event is not streamed at all
        assertThat(streamed).extracting(e -> e.eventPayloads().get(0).getValue("/connection/name").orElseThrow()
                .asString()).containsExactly("b1", "a1", "c1", "a3");
        assertThat(streamed).extracting(PersistedJournalEntry::cursor).doesNotHaveDuplicates();
        assertThat(streamed).allSatisfy(entry -> assertThat(entry.eventPayloads()).hasSize(1));

        // resuming behind the second streamed entry yields exactly the third and the fourth
        final List<PersistedJournalEntry> resumed = await(underTest.streamJournalEntries(streamed.get(1).cursor(),
                JOURNAL_POINTERS, 10).runWith(Sink.seq(), mat));
        assertThat(resumed).extracting(PersistedJournalEntry::cursor)
                .containsExactly(streamed.get(2).cursor(), streamed.get(3).cursor());
    }

    @Test
    public void rewriteJournalEntriesLeavesAnEqualPayloadUntouchedAndRewritesAChangedOne() throws Exception {
        insertEvent("connection:a", 1L, journalSecret("a1"));
        insertEvent("connection:a", 2L, journalSecret("a2"));
        final List<PersistedJournalEntry> streamed = await(underTest.streamJournalEntries(null, JOURNAL_POINTERS, 10)
                .runWith(Sink.seq(), mat));
        final String xminUnchangedBefore = xmin("connections_journal", "connection:a", 1L);
        final String xminChangedBefore = xmin("connections_journal", "connection:a", 2L);
        final PersistedJournalEntry unchanged = streamed.get(0).withEventPayloads(streamed.get(0).eventPayloads());
        final PersistedJournalEntry changed = streamed.get(1).withEventPayloads(List.of(
                streamed.get(1).eventPayloads().get(0).setValue("/connection/uri", "amqps://u:encrypted_x@h")));

        assertThat(await(underTest.rewriteJournalEntries(List.of(unchanged)))).as("no row updated").isZero();
        assertThat(await(underTest.rewriteJournalEntries(List.of(unchanged, changed)))).isEqualTo(1L);

        assertThat(xmin("connections_journal", "connection:a", 1L)).as("the row was never written")
                .isEqualTo(xminUnchangedBefore);
        assertThat(xmin("connections_journal", "connection:a", 2L)).isNotEqualTo(xminChangedBefore);
        assertThat(eventText("connection:a", 1L)).doesNotContain("encrypted_");
        assertThat(eventText("connection:a", 2L)).contains("encrypted_x");
    }

    // --- progress ---

    @Test
    public void progressIsSavedLoadedReplacedAndDeleted() throws Exception {
        assertThat(await(underTest.loadProgress())).isEmpty();

        await(underTest.saveProgress(progress("snapshots", 150L)));
        final Optional<org.eclipse.ditto.json.JsonObject> first = await(underTest.loadProgress());
        assertThat(first).isPresent();
        assertThat(first.get().getValue("phase").orElseThrow().asString()).isEqualTo("snapshots");
        assertThat(first.get().getValue("snapshotsProcessed").orElseThrow().asLong()).isEqualTo(150L);
        assertThat(first.get().getValue("lastProcessedJournalId").orElseThrow().isNull()).isTrue();

        await(underTest.saveProgress(progress("completed", 300L)));
        assertThat(count("SELECT count(*) FROM " + PostgresSchema.ENCRYPTION_MIGRATION_TABLE)).isEqualTo(1L);
        assertThat(await(underTest.loadProgress()).orElseThrow().getValue("snapshotsProcessed").orElseThrow().asLong())
                .isEqualTo(300L);

        await(underTest.deleteProgress());
        assertThat(await(underTest.loadProgress())).isEmpty();
        await(underTest.deleteProgress()); // deleting nothing is fine
    }

    // --- cursors and close ---

    @Test
    public void anUnparsableCursorFailsTheStream() throws Exception {
        insertSnapshot("connection:a", 1L, T0, secret("a"));

        assertThat(failureOf(underTest.streamSnapshots("68f1a2b3c4d5e6f7a8b9c0d1", SNAPSHOT_POINTERS, 10)
                .runWith(Sink.seq(), mat)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("68f1a2b3c4d5e6f7a8b9c0d1");
        assertThat(failureOf(underTest.streamJournalEntries("{\"sn\":1}", JOURNAL_POINTERS, 10)
                .runWith(Sink.seq(), mat)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{\"sn\":1}");
    }

    @Test
    public void closeKeepsThePoolUsable() throws Exception {
        insertSnapshot("connection:a", 1L, T0, secret("a"));

        underTest.close();

        assertThat(await(underTest.streamSnapshots(null, SNAPSHOT_POINTERS, 10).runWith(Sink.seq(), mat))).hasSize(1);
        assertThat(client.getConnectionPool().isDisposed()).isFalse();
    }

    // --- helpers ---

    private static String secret(final String name) {
        return "{\"uri\":\"amqps://user:secret@host:5671\",\"credentials\":{\"password\":\"pw\"},\"name\":\"" + name
                + "\"}";
    }

    private static String journalSecret(final String name) {
        return "{\"type\":\"connectivity.events:connectionCreated\",\"connection\":{\"uri\":"
                + "\"amqps://user:secret@host:5671\",\"name\":\"" + name + "\"}}";
    }

    private static org.eclipse.ditto.json.JsonObject progress(final String phase, final long snapshotsProcessed) {
        return org.eclipse.ditto.json.JsonFactory.newObjectBuilder()
                .set("phase", phase)
                .set("lastProcessedSnapshotId", "{\"sn\":1,\"writtenAt\":\"2026-11-06T10:00:00Z\",\"pid\":\"p\"}")
                .set("lastProcessedJournalId", (String) null)
                .set("snapshotsProcessed", snapshotsProcessed)
                .set("startedAt", "2026-11-06T10:00:00Z")
                .build();
    }

    private static void insertSnapshot(final String pid, final long sn, final Instant writtenAt, final String json)
            throws Exception {
        execute("INSERT INTO connections_snaps (pid, sn, snapshot, lifecycle, written_at) "
                + "VALUES ($1, $2, $3::jsonb, NULL, $4)", stmt -> {
            stmt.bind(0, pid);
            stmt.bind(1, sn);
            stmt.bind(2, json);
            stmt.bind(3, writtenAt);
        });
    }

    private static void insertEvent(final String pid, final long sn, final String json) throws Exception {
        execute("INSERT INTO connections_journal (pid, sn, manifest, tags, event, written_at) "
                + "VALUES ($1, $2, $3, '{}', $4::jsonb, clock_timestamp())", stmt -> {
            stmt.bind(0, pid);
            stmt.bind(1, sn);
            stmt.bind(2, "connectivity.events:connectionCreated");
            stmt.bind(3, json);
        });
    }

    private static String snapshotText(final String pid, final long sn, final Instant writtenAt) throws Exception {
        return await(client.executeSql("SELECT snapshot::text AS s FROM connections_snaps WHERE pid = $1 AND sn = $2 "
                        + "AND written_at = $3", stmt -> {
                    stmt.bind(0, pid);
                    stmt.bind(1, sn);
                    stmt.bind(2, writtenAt);
                }, (row, meta) -> row.get("s", String.class))
                .runWith(Sink.head(), mat));
    }

    private static String eventText(final String pid, final long sn) throws Exception {
        return await(client.executeSql("SELECT event::text AS e FROM connections_journal WHERE pid = $1 AND sn = $2",
                        stmt -> {
                            stmt.bind(0, pid);
                            stmt.bind(1, sn);
                        }, (row, meta) -> row.get("e", String.class))
                .runWith(Sink.head(), mat));
    }

    private static String xmin(final String table, final String pid, final long sn) throws Exception {
        return await(client.executeSql("SELECT xmin::text AS x FROM " + table + " WHERE pid = $1 AND sn = $2",
                        stmt -> {
                            stmt.bind(0, pid);
                            stmt.bind(1, sn);
                        }, (row, meta) -> row.get("x", String.class))
                .runWith(Sink.head(), mat));
    }

    private static long count(final String sql) throws Exception {
        return await(client.executeSql(sql, null, (row, meta) -> row.get(0, Long.class)).runWith(Sink.head(), mat));
    }

    private static void execute(final String sql, final Consumer<Statement> binder) throws Exception {
        await(client.executeUpdate(sql, binder).runWith(Sink.ignore(), mat));
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    private static Throwable failureOf(final CompletionStage<?> stage) throws Exception {
        try {
            stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
        } catch (final ExecutionException e) {
            return e.getCause();
        }
        throw new AssertionError("expected the stage to fail");
    }

}
