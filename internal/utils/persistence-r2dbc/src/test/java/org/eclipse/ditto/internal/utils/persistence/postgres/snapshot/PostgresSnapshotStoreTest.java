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
package org.eclipse.ditto.internal.utils.persistence.postgres.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.eclipse.ditto.internal.utils.persistence.postgres.payload.PekkoSerializationPayloadCodec;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.sharding.internal.EventSourcedRememberEntitiesShardStore;
import org.apache.pekko.persistence.SelectedSnapshot;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.persistence.SnapshotSelectionCriteria;
import org.apache.pekko.serialization.Serialization;
import org.apache.pekko.serialization.SerializationExtension;
import org.apache.pekko.testkit.javadsl.TestKit;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Unit tests for the {@link PostgresSnapshotStore}, exercised through {@link PostgresSnapshotStoreOps} (the plain
 * delegate the actor forwards to, so no {@code ActorSystem} is needed): the criteria binding (all four
 * bounds, Long.MAX_VALUE guarded against TIMESTAMPTZ overflow), the idempotent upsert with {@code written_at} from
 * {@code SnapshotMetadata.timestamp}, and the two distinct {@code deleteAsync} overloads — offline against a
 * {@link RecordingConnectionFactory}.
 */
public final class PostgresSnapshotStoreTest {

    private static final String PID = "thing:ns:id";

    private static ActorSystem system;
    private static Serialization serialization;

    @BeforeClass
    public static void startActorSystem() {
        system = ActorSystem.create("PostgresSnapshotStoreTest", ConfigFactory.load());
        serialization = SerializationExtension.get(system);
    }

    @AfterClass
    public static void stopActorSystem() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
            system = null;
        }
    }

    private PostgresSnapshotStoreOps storeFor(final RecordingConnectionFactory factory) {
        final DittoPostgresClient client = StubClientSupport.clientFor(factory);
        return PostgresSnapshotStoreOps.of(PostgresPersistenceOperations.of(client, "things"));
    }

    private static <T> T await(final java.util.concurrent.CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    public void loadAsyncHonorsAllFourCriteriaBounds() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory()
                .onSql("FROM things_snaps", List.of(Map.of(
                        "pid", PID, "sn", 10L, "snapshot", "{\"y\":2}",
                        "lifecycle", "ACTIVE", "written_at", Instant.ofEpochMilli(1234L))));
        final PostgresSnapshotStoreOps store = storeFor(factory);

        final Optional<SelectedSnapshot> loaded =
                await(store.loadAsync(PID, SnapshotSelectionCriteria.create(10L, 5000L, 3L, 100L)));

        assertThat(loaded).isPresent();
        assertThat(loaded.get().metadata().sequenceNr()).isEqualTo(10L);
        final String sql = factory.executedSql().stream()
                .filter(s -> s.contains("FROM things_snaps")).findFirst().orElseThrow();
        // all four bounds + the descending order + LIMIT 1.
        assertThat(sql).contains("sn <= $2");
        assertThat(sql).contains("sn >= $3");
        assertThat(sql).contains("written_at <= $4");
        assertThat(sql).contains("written_at >= $5");
        assertThat(sql).contains("ORDER BY sn DESC, written_at DESC LIMIT 1");
    }

    @Test
    public void loadAsyncWithLatestCriteriaDoesNotOverflow() throws Exception {
        // Pekko's recovery passes criteria.maxTimestamp = Long.MAX_VALUE; binding must not overflow TIMESTAMPTZ.
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = storeFor(factory);

        final Optional<SelectedSnapshot> loaded =
                await(store.loadAsync(PID, SnapshotSelectionCriteria.latest()));

        assertThat(loaded).isEmpty();
        assertThat(factory.executedContaining("FROM things_snaps")).isTrue();
    }

    @Test
    public void saveAsyncIsIdempotentUpsertWithWrittenAtFromMetadata() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = storeFor(factory);

        await(store.saveAsync(new SnapshotMetadata(PID, 12L, 9999L), "{\"y\":2}"));

        final String sql = factory.executedSql().stream()
                .filter(s -> s.contains("INSERT INTO things_snaps")).findFirst().orElseThrow();
        // idempotent ON CONFLICT upsert keyed on (pid, sn, written_at).
        assertThat(sql).contains("ON CONFLICT (pid, sn, written_at) DO UPDATE");
        // written_at bound from metadata.timestamp (not now()): $5 is the bind, never now().
        assertThat(sql).contains("$5");
        assertThat(sql).doesNotContain("now()");
    }

    @Test
    public void saveAsyncSerializationFailureSurfacesAsFailedFutureNotSynchronousThrow() {
        // Pekko's SnapshotStore contract requires saveAsync to report failures via the returned Future, not by
        // throwing synchronously (L-13). An unsupported snapshot payload (e.g. the BsonValue the Mongo adapter would
        // produce, here represented by a plain Object that toJsonText rejects) must therefore NOT throw out of
        // saveAsync — the IllegalArgumentException must be delivered through the CompletionStage.
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = storeFor(factory);
        final Object unsupportedSnapshot = new Object();

        // 1. Invoking saveAsync must NOT throw synchronously; it must hand back a (failed) stage.
        final CompletionStage<Void> stage = store.saveAsync(new SnapshotMetadata(PID, 12L, 9999L), unsupportedSnapshot);
        assertThat(stage).isNotNull();

        // 2. The serialization failure must be observable as a failed Future, not a thrown exception.
        assertThatCode(() -> stage.toCompletableFuture().get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);

        // 3. No SQL must have been executed: serialization failed before any DB interaction.
        assertThat(factory.executedSql()).isEmpty();
    }

    @Test
    public void deleteAsyncByMetadataDeletesExactRow() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = storeFor(factory);

        await(store.deleteAsync(new SnapshotMetadata(PID, 12L, 9999L)));

        final String sql = factory.executedSql().stream()
                .filter(s -> s.contains("DELETE FROM things_snaps")).findFirst().orElseThrow();
        // metadata form: exact (pid, sn, written_at).
        assertThat(sql).contains("WHERE pid = $1 AND sn = $2 AND written_at = $3");
    }

    @Test
    public void deleteAsyncByCriteriaIsUpperBoundedOnly() throws Exception {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = storeFor(factory);

        await(store.deleteAsync(PID, SnapshotSelectionCriteria.create(20L, 5000L)));

        final String sql = factory.executedSql().stream()
                .filter(s -> s.contains("DELETE FROM things_snaps")).findFirst().orElseThrow();
        // criteria form: upper-bounded sn<=maxSeq AND written_at<=maxTs, no lower bound.
        assertThat(sql).contains("WHERE pid = $1 AND sn <= $2 AND written_at <= $3");
    }

    @Test
    public void pekkoSerializationCodecSavesTheEnvelopeAndLoadsTheDeserializedState() throws Exception {
        // The remember-entities store snapshots its State as Pekko-serialized bytes: the connection_remember snapshot
        // store wraps them in the JSON envelope on save and offers the deserialized State on load.
        final String pid = "/sharding/connectionShard/3";
        final Object state = new EventSourcedRememberEntitiesShardStore.State(
                CollectionConverters.asScala(Set.of("conn-a", "conn-b")).toSet());
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = PostgresSnapshotStoreOps.of(
                PostgresPersistenceOperations.of(StubClientSupport.clientFor(factory), "connection_remember"),
                PekkoSerializationPayloadCodec.of(serialization));

        await(store.saveAsync(new SnapshotMetadata(pid, 4L, 9999L), state));

        final RecordingConnectionFactory.Execution insert = factory.executions().stream()
                .filter(execution -> execution.sql().contains("INSERT INTO connection_remember_snaps"))
                .findFirst().orElseThrow();
        final String stored = (String) insert.binds().get(2);
        assertThat(JsonFactory.newObject(stored).getKeys().stream().map(CharSequence::toString))
                .containsExactlyInAnyOrder("serializerId", "manifest", "bytes");
        assertThat(insert.binds().get(3)).as("no lifecycle is derived from an envelope").isNull();

        factory.onSql("FROM connection_remember_snaps", List.of(Map.of(
                "pid", pid, "sn", 4L, "snapshot", stored, "written_at", Instant.ofEpochMilli(9999L))));
        final Optional<SelectedSnapshot> loaded = await(store.loadAsync(pid, SnapshotSelectionCriteria.latest()));

        assertThat(loaded).isPresent();
        assertThat(loaded.get().snapshot()).isEqualTo(state);
        assertThat(loaded.get().metadata().sequenceNr()).isEqualTo(4L);
    }

    @Test
    public void pekkoSerializationCodecReportsAnUnserializableSnapshotAsAFailedFuture() {
        // as for the JSON text codec: Pekko's SnapshotStore contract wants the failure in the future, not thrown
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresSnapshotStoreOps store = PostgresSnapshotStoreOps.of(
                PostgresPersistenceOperations.of(StubClientSupport.clientFor(factory), "connection_remember"),
                PekkoSerializationPayloadCodec.of(serialization));

        final CompletionStage<Void> stage = store.saveAsync(new SnapshotMetadata(PID, 1L, 1L), new Object());

        assertThatCode(() -> stage.toCompletableFuture().get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to Pekko-serialize");
        assertThat(factory.executedContaining("INSERT INTO")).as("nothing reaches the database").isFalse();
    }

}
