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

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.models.streaming.StreamedSnapshot;
import org.eclipse.ditto.internal.models.streaming.SudoStreamSnapshots;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.NoOpCloseable;
import org.eclipse.ditto.internal.utils.persistence.api.streaming.SnapshotStreamingActor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SourceRef;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.TestProbe;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Integration test for the Postgres snapshot-streaming actor (the provider's {@code streaming(...)} wiring)
 * against a real PostgreSQL (PG 16) via Testcontainers. Runs under failsafe ({@code *IT}); skipped offline / when no
 * Docker daemon is reachable.
 * <p>
 * Stands up the backend-neutral {@link SnapshotStreamingActor} with exactly the collaborators the Postgres provider's
 * {@code streaming(...)} supplies — a real {@link PostgresReadJournal} over the container and a {@link NoOpCloseable} —
 * saves snapshots, then issues a {@code SudoStreamSnapshots} command and consumes the returned {@link SourceRef},
 * proving the snapshots stream end-to-end through {@code getNewestSnapshotsAbove}.
 */
public final class PostgresStreamingIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static final EntityType THING_TYPE = EntityType.of("thing");
    private static final String PID_PREFIX = "thing:";

    private static ActorSystem system;
    private static Materializer mat;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;
    private static PostgresSnapshotStoreOps snapshots;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresStreamingIT", t);
        }
        final ConnectionFactory factory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresStreamingIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("streaming-it-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
        snapshots = PostgresSnapshotStoreOps.of(operations);
        readJournal = PostgresReadJournal.of(operations);
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        POSTGRES.stop();
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void streamingActorStreamsPostgresSnapshotsEndToEnd() throws Exception {
        // Save two snapshots (different pids) — written_at in the past so the minAgeFromNow=0 filter selects them.
        await(snapshots.saveAsync(new SnapshotMetadata("thing:strm:a", 3L, 1000L), "{\"v\":\"a\"}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:strm:b", 7L, 2000L), "{\"v\":\"b\"}"));

        // The PID<->entity-id mapping functions the service supplies; for "thing" the pid is "thing:" + entityId.
        final Function<String, EntityId> pid2EntityId =
                pid -> EntityId.of(THING_TYPE, pid.substring(PID_PREFIX.length()));
        final Function<EntityId, String> entityId2Pid = entityId -> PID_PREFIX + entityId;

        final TestProbe pubSub = TestProbe.apply("pubSub", system);
        // Exactly the collaborators the provider's streaming(...) wires: the real Postgres read journal + a NoOpCloseable.
        final Props props = SnapshotStreamingActor.propsForTest(pid2EntityId, entityId2Pid, readJournal,
                NoOpCloseable.getInstance(), pubSub.ref());
        final ActorRef streamingActor = system.actorOf(props);

        final TestKit requester = new TestKit(system);
        final SudoStreamSnapshots command = SudoStreamSnapshots.of(
                100,                              // burst
                20_000L,                          // timeoutMillis
                List.of("v"),                     // snapshot fields to project
                DittoHeaders.empty(),
                THING_TYPE);
        streamingActor.tell(command, requester.getRef());

        final SourceRef<StreamedSnapshot> sourceRef = requester.expectMsgClass(SourceRef.class);
        final List<StreamedSnapshot> streamed = sourceRef.getSource()
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);

        final List<String> entityIds = streamed.stream()
                .map(s -> s.getEntityId().toString())
                .collect(Collectors.toList());
        assertThat(entityIds).contains("strm:a", "strm:b");
    }

    @Test
    public void readJournalStreamsNewestSnapshotsAbove() throws Exception {
        await(snapshots.saveAsync(new SnapshotMetadata("thing:read:x", 5L, 1000L), "{\"v\":5}"));

        final long count = readJournal.getNewestSnapshotsAbove("", 100, false, Duration.ZERO, mat)
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS)
                .stream()
                .filter(entry -> entry.getPid().filter(p -> p.equals("thing:read:x")).isPresent())
                .count();
        assertThat(count).isEqualTo(1L);
    }
}
