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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresClientExtension;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Offline unit tests for {@link PostgresPersistedPayloadStore}: the SQL text as shipped (every value a bound
 * parameter), the pointer-to-path translation, the argument checks of the SPI, the failure modes that must not throw
 * synchronously, what the store does without a round trip, and the table binding of a store built on the shared pool.
 * Statement execution against a real database is {@link PostgresPersistedPayloadStoreIT}'s job.
 */
public final class PostgresPersistedPayloadStoreTest {

    private static final List<String> POINTERS = List.of("/uri", "/credentials/password");
    private static final Instant WRITTEN_AT = Instant.parse("2026-11-06T10:15:30.123456Z");

    private static ActorSystem system;

    @BeforeClass
    public static void setUp() {
        // a complete-enough ditto.postgresql so PostgresClientExtension can build the shared pool without connecting
        system = ActorSystem.create("PostgresPersistedPayloadStoreTest", ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"r2dbc:postgresql://localhost:5432/ditto\"\n"
                                + "ditto.postgresql.username = \"ditto\"\n"
                                + "ditto.postgresql.password = \"secret\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n")
                .withFallback(ConfigFactory.load()));
    }

    @AfterClass
    public static void tearDown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    // --- SQL as shipped ---

    @Test
    public void snapshotPageSqlFromTheBeginning() {
        assertThat(PostgresPersistedPayloadStore.snapshotPageSql("connections_snaps", false, 2)).isEqualTo(
                "SELECT pid, sn, written_at, snapshot::text AS snapshot FROM connections_snaps "
                        + "WHERE (snapshot #> $1::text[] IS NOT NULL OR snapshot #> $2::text[] IS NOT NULL) "
                        + "ORDER BY pid, sn, written_at LIMIT $3");
    }

    @Test
    public void snapshotPageSqlBehindACursor() {
        assertThat(PostgresPersistedPayloadStore.snapshotPageSql("connections_snaps", true, 2)).isEqualTo(
                "SELECT pid, sn, written_at, snapshot::text AS snapshot FROM connections_snaps "
                        + "WHERE (pid, sn, written_at) > ($1, $2, $3) "
                        + "AND (snapshot #> $4::text[] IS NOT NULL OR snapshot #> $5::text[] IS NOT NULL) "
                        + "ORDER BY pid, sn, written_at LIMIT $6");
    }

    @Test
    public void journalPageSqlFromTheBeginningAndBehindACursor() {
        assertThat(PostgresPersistedPayloadStore.journalPageSql("connections_journal", false, 1)).isEqualTo(
                "SELECT pid, sn, seq, event::text AS event FROM connections_journal "
                        + "WHERE (event #> $1::text[] IS NOT NULL) ORDER BY seq LIMIT $2");
        assertThat(PostgresPersistedPayloadStore.journalPageSql("connections_journal", true, 2)).isEqualTo(
                "SELECT pid, sn, seq, event::text AS event FROM connections_journal "
                        + "WHERE seq > $1 AND (event #> $2::text[] IS NOT NULL OR event #> $3::text[] IS NOT NULL) "
                        + "ORDER BY seq LIMIT $4");
    }

    @Test
    public void rewriteAndProgressSql() {
        assertThat(PostgresPersistedPayloadStore.updateSnapshotSql("connections_snaps")).isEqualTo(
                "UPDATE connections_snaps SET snapshot = $1::jsonb WHERE pid = $2 AND sn = $3 AND written_at = $4");
        assertThat(PostgresPersistedPayloadStore.updateJournalEventSql("connections_journal")).isEqualTo(
                "UPDATE connections_journal SET event = $1::jsonb WHERE pid = $2 AND sn = $3");
        assertThat(PostgresPersistedPayloadStore.saveProgressSql("connections_encryption_migration")).isEqualTo(
                "INSERT INTO connections_encryption_migration (id, progress, updated_at) "
                        + "VALUES ($1, $2::jsonb, clock_timestamp()) "
                        + "ON CONFLICT (id) DO UPDATE SET progress = EXCLUDED.progress, "
                        + "updated_at = EXCLUDED.updated_at");
        assertThat(PostgresPersistedPayloadStore.loadProgressSql("connections_encryption_migration"))
                .isEqualTo("SELECT progress::text AS progress FROM connections_encryption_migration WHERE id = $1");
        assertThat(PostgresPersistedPayloadStore.deleteProgressSql("connections_encryption_migration"))
                .isEqualTo("DELETE FROM connections_encryption_migration WHERE id = $1");
    }

    // --- pointers and cursors ---

    @Test
    public void pointersBecomeTextArrayPaths() {
        assertThat(PostgresPersistedPayloadStore.pathOf("/uri")).containsExactly("uri");
        assertThat(PostgresPersistedPayloadStore.pathOf("/connection/credentials/password"))
                .containsExactly("connection", "credentials", "password");
        // a dotted key, which MongoDB's dotted-path $exists cannot address, is one path element here
        assertThat(PostgresPersistedPayloadStore.pathOf("/a.b/c d")).containsExactly("a.b", "c d");
        assertThat(PostgresPersistedPayloadStore.requiredPaths(POINTERS))
                .containsExactly(new String[]{"uri"}, new String[]{"credentials", "password"});
    }

    @Test
    public void emptyOrNullPointersAreRejectedSynchronously() {
        final PostgresPersistedPayloadStore underTest = storeOn(new RecordingConnectionFactory());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.streamSnapshots(null, List.of(), 10))
                .withMessageContaining("must not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.streamJournalEntries(null, List.of(), 10));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> underTest.streamSnapshots(null, null, 10));
    }

    @Test
    public void journalCursorIsTheDecimalSeq() {
        assertThat(PostgresPersistedPayloadStore.parseJournalCursor("42")).isEqualTo(42L);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PostgresPersistedPayloadStore.parseJournalCursor("68f1a2b3c4d5e6f7a8b9c0d1"))
                .withMessageContaining("68f1a2b3c4d5e6f7a8b9c0d1");
    }

    @Test
    public void anUnparsableCursorFailsTheStreamNotTheCall() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresPersistedPayloadStore underTest = storeOn(factory);

        final CompletionStage<List<PersistedSnapshot>> snapshots = underTest
                .streamSnapshots("68f1a2b3c4d5e6f7a8b9c0d1", POINTERS, 10)
                .runWith(Sink.seq(), system);
        final CompletionStage<List<PersistedJournalEntry>> entries = underTest
                .streamJournalEntries("not-a-seq", POINTERS, 10)
                .runWith(Sink.seq(), system);

        assertThat(failureOf(snapshots)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("68f1a2b3c4d5e6f7a8b9c0d1");
        assertThat(failureOf(entries)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-seq");
        assertThat(factory.executedSql()).as("no statement is sent for a cursor that cannot be parsed").isEmpty();
    }

    @Test
    public void everyPageAfterTheFirstStartsBehindThePreviousPagesLastRow() throws Exception {
        // the first page may start from the beginning (no cursor predicate); the second page MUST carry the cursor
        // predicate and bind the last row's key, whatever the first page started from
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .onSql("WHERE (pid, sn, written_at) > ($1, $2, $3)", List.of())
                .onSql("WHERE (snapshot #> $1::text[]", List.of(
                        snapshotRow("connection:a", 1L, WRITTEN_AT, "{\"uri\":\"a\"}"),
                        snapshotRow("connection:b", 2L, WRITTEN_AT.plusSeconds(1), "{\"uri\":\"b\"}")));
        final PostgresPersistedPayloadStore underTest = storeOn(factory);

        final List<PersistedSnapshot> streamed = await(underTest.streamSnapshots(null, POINTERS, 2)
                .runWith(Sink.seq(), system));

        assertThat(streamed).extracting(PersistedSnapshot::pid).containsExactly("connection:a", "connection:b");
        assertThat(factory.executedSql()).containsExactly(
                PostgresPersistedPayloadStore.snapshotPageSql("connections_snaps", false, 2),
                PostgresPersistedPayloadStore.snapshotPageSql("connections_snaps", true, 2));
        final RecordingConnectionFactory.Execution secondPage = factory.executions().get(1);
        assertThat(secondPage.binds().get(0)).isEqualTo("connection:b");
        assertThat(secondPage.binds().get(1)).isEqualTo(2L);
        assertThat(secondPage.binds().get(2)).isEqualTo(WRITTEN_AT.plusSeconds(1));
        assertThat(secondPage.binds().get(3)).isEqualTo(new String[]{"uri"});
        assertThat(secondPage.binds().get(4)).isEqualTo(new String[]{"credentials", "password"});
        assertThat(secondPage.binds().get(5)).isEqualTo(2);
    }

    @Test
    public void aShortFirstPageEndsTheStreamWithoutASecondStatement() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .onSql("FROM connections_journal WHERE (event #> $1::text[]", List.of(
                        journalRow("connection:a", 1L, 7L, "{\"connection\":{\"uri\":\"a\"}}")));
        final PostgresPersistedPayloadStore underTest = storeOn(factory);

        final List<PersistedJournalEntry> streamed = await(underTest.streamJournalEntries(null, POINTERS, 2)
                .runWith(Sink.seq(), system));

        assertThat(streamed).extracting(PersistedJournalEntry::cursor).containsExactly("7");
        assertThat(factory.executedSql()).hasSize(1);
    }

    @Test
    public void aNonPositiveBatchSizeIsRejected() {
        final PostgresPersistedPayloadStore underTest = storeOn(new RecordingConnectionFactory());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.streamSnapshots(null, POINTERS, 0))
                .withMessageContaining("batchSize");
    }

    // --- rewrites and progress without a round trip ---

    @Test
    public void rewritesOfAnEmptyListCompleteWithZeroWithoutARoundTrip() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresPersistedPayloadStore underTest = storeOn(factory);

        assertThat(await(underTest.rewriteSnapshots(List.of()))).isZero();
        assertThat(await(underTest.rewriteJournalEntries(List.of()))).isZero();
        assertThat(factory.executedSql()).isEmpty();
    }

    @Test
    public void rewriteJournalEntriesIssuesNoStatementForAnUnchangedEntry() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresPersistedPayloadStore underTest = storeOn(factory);
        final PostgresPersistedJournalEntry stored = PostgresPersistedJournalEntry.of("connection:c", 1L, 5L,
                "{\"connection\":{\"uri\":\"amqps://u:p@h\"}}");
        final PersistedJournalEntry unchanged = stored.withEventPayloads(stored.eventPayloads());

        assertThat(await(underTest.rewriteJournalEntries(List.of(unchanged)))).isZero();
        assertThat(factory.executedSql()).isEmpty();
    }

    @Test
    public void rewritesRejectEntriesNotStreamedByThisStore() {
        final PostgresPersistedPayloadStore underTest = storeOn(new RecordingConnectionFactory());
        final PersistedSnapshot foreignSnapshot = new ForeignSnapshot();
        final PersistedJournalEntry foreignEntry = new ForeignJournalEntry();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.rewriteSnapshots(List.of(foreignSnapshot)))
                .withMessageContaining("PostgresPersistedSnapshot")
                .withMessageContaining(ForeignSnapshot.class.getName());
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.rewriteJournalEntries(List.of(foreignEntry)))
                .withMessageContaining("PostgresPersistedJournalEntry");
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> underTest.rewriteSnapshots(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> underTest.rewriteJournalEntries(null));
    }

    @Test
    public void saveProgressWithANonFlatRecordFailsTheStageInsteadOfThrowing() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresPersistedPayloadStore underTest = storeOn(factory);
        final JsonObject nested = JsonFactory.newObjectBuilder()
                .set("phase", "snapshots")
                .set("counters", JsonFactory.newObjectBuilder().set("processed", 1).build())
                .build();
        final JsonObject withArray = JsonFactory.newObjectBuilder()
                .set("ids", JsonFactory.newArrayBuilder().add("a").build())
                .build();

        final CompletionStage<Void> nestedStage = underTest.saveProgress(nested);
        final CompletionStage<Void> arrayStage = underTest.saveProgress(withArray);

        assertThat(failureOf(nestedStage)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<counters>");
        assertThat(failureOf(arrayStage)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("<ids>");
        assertThat(factory.executedSql()).isEmpty();
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> underTest.saveProgress(null));
    }

    @Test
    public void flatRecordsOfScalarsAndNullsAreAccepted() {
        final JsonObject flat = JsonFactory.newObjectBuilder()
                .set("phase", "snapshots")
                .set("lastProcessedSnapshotId", (String) null)
                .set("snapshotsProcessed", 150L)
                .set("dryRun", false)
                .build();

        assertThat(PostgresPersistedPayloadStore.flatRecordText(flat)).isEqualTo(flat.toString());
    }

    // --- the store on the shared pool ---

    @Test
    public void newInstanceBindsTheConnectionEntityTypeToTheConnectionsTablesOnTheSharedClient() {
        final PostgresPersistedPayloadStore underTest = PostgresPersistedPayloadStore.newInstance("connection", system);

        assertThat(underTest.tables().snapsTable()).isEqualTo("connections_snaps");
        assertThat(underTest.tables().journalTable()).isEqualTo("connections_journal");
        assertThat(underTest.progressTable()).isEqualTo(PostgresSchema.ENCRYPTION_MIGRATION_TABLE);
        assertThat(underTest.client()).isSameAs(PostgresClientExtension.get(system).getClient());
        assertThatCode(underTest::close).doesNotThrowAnyException();
        assertThat(underTest.client().getConnectionPool().isDisposed()).as("close() leaves the shared pool open")
                .isFalse();
    }

    @Test
    public void newInstanceForAnUnknownEntityTypeFailsClearly() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PostgresPersistedPayloadStore.newInstance("gateway", system))
                .withMessageContaining("gateway");
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedPayloadStore.newInstance(null, system));
    }

    // --- helpers ---

    private static Map<String, Object> snapshotRow(final String pid, final long sn, final Instant writtenAt,
            final String snapshot) {
        return Map.of("pid", pid, "sn", sn, "written_at", writtenAt, "snapshot", snapshot);
    }

    private static Map<String, Object> journalRow(final String pid, final long sn, final long seq,
            final String event) {
        return Map.of("pid", pid, "sn", sn, "seq", seq, "event", event);
    }

    private static PostgresPersistedPayloadStore storeOn(final RecordingConnectionFactory factory) {
        final DittoPostgresClient client = StubClientSupport.clientFor(factory);
        return PostgresPersistedPayloadStore.of(client, "connections");
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static Throwable failureOf(final CompletionStage<?> stage) throws Exception {
        final CompletableFuture<?> future = stage.toCompletableFuture();
        try {
            future.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        } catch (final ExecutionException e) {
            return e.getCause();
        }
        throw new AssertionError("expected the stage to fail, but it completed with " + future.getNow(null));
    }

    private static final class ForeignSnapshot implements PersistedSnapshot {

        @Override
        public String cursor() {
            return "x";
        }

        @Override
        public String pid() {
            return "connection:x";
        }

        @Override
        public JsonObject payload() {
            return JsonObject.empty();
        }

        @Override
        public PersistedSnapshot withPayload(final JsonObject payload) {
            return this;
        }
    }

    private static final class ForeignJournalEntry implements PersistedJournalEntry {

        @Override
        public String cursor() {
            return "1";
        }

        @Override
        public String pid() {
            return "connection:x";
        }

        @Override
        public List<JsonObject> eventPayloads() {
            return List.of();
        }

        @Override
        public PersistedJournalEntry withEventPayloads(final List<JsonObject> payloads) {
            return this;
        }
    }

}
