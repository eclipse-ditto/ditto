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
package org.eclipse.ditto.timeseries.service.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Status;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Integration test for {@link TimeseriesIngestActor} against a real MongoDB instance, exercising
 * the full pekko-contrib-mongodb-persistence journal round-trip (jackson-cbor serialization of
 * {@code IngestReceivedEvent} / {@code IngestAppliedEvent} → MongoDB → recovery on restart).
 * <p>
 * Skipped by default. Set the environment variable
 * {@code TIMESERIES_PERSISTENCE_TEST_URI=mongodb://localhost:27017} (or whichever URI points at
 * your dev MongoDB) to enable. Each test gets a unique database name so concurrent runs do not
 * collide; the database is dropped in {@code @AfterClass}.
 */
public final class TimeseriesIngestActorMongoJournalIT {

    private static final String ENV_VAR = "TIMESERIES_PERSISTENCE_TEST_URI";
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");

    private static String mongoUri;
    private static String databaseName;
    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        mongoUri = System.getenv(ENV_VAR);
        Assume.assumeTrue(
                "Set " + ENV_VAR + "=mongodb://localhost:27017 (or your dev URI) to enable this IT",
                mongoUri != null && !mongoUri.isEmpty());

        // Per-run database keeps repeated local runs hermetic and ensures parallel CI shards
        // can't trample one another's journals.
        databaseName = "ditto_ts_journal_it_" + UUID.randomUUID().toString().replace("-", "");
        actorSystem = ActorSystem.create("TimeseriesIngestActorMongoJournalIT", buildConfig());
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        if (mongoUri != null && databaseName != null) {
            final MongoClient client = MongoClients.create(new ConnectionString(mongoUri));
            try {
                blockUntilComplete(client.getDatabase(databaseName).drop());
            } finally {
                client.close();
            }
        }
    }

    private RecordingAdapter adapter;
    private ThingId thingId;

    @Before
    public void setUp() {
        adapter = new RecordingAdapter();
        // Random thingId per test → unique persistenceId → tests do not see each other's journal.
        thingId = ThingId.of("it.timeseries.test", "sensor-" + UUID.randomUUID());
    }

    @After
    public void tearDown() {
        adapter = null;
    }

    @Test
    public void appliedBatchSurvivesRestartAndDedupesOnRetry() {
        // The ack-lost-in-transit dedup window must survive an actor restart: once
        // IngestAppliedEvent has been journaled, a replayed correlation-id must be acked
        // idempotently without re-writing the timeseries. This exercises the full
        // jackson-cbor → MongoDB → recovery → ring-rehydration path.
        new TestKit(actorSystem) {{
            final ActorRef first = startEntity(adapter, thingId);
            final String corrId = "applied-" + UUID.randomUUID();
            final IngestDataPoints command = IngestDataPoints.of(thingId, sampleBatch(thingId),
                    DittoHeaders.newBuilder().correlationId(corrId).build());

            first.tell(command, getRef());
            expectMsgClass(IngestDataPointsResponse.class);
            assertThat(adapter.writtenBatches).hasSize(1);

            watch(first);
            first.tell(PoisonPill.getInstance(), ActorRef.noSender());
            expectTerminated(first);

            // Restart against the same persistenceId — recovery should rebuild the
            // recently-applied ring from the journal.
            final RecordingAdapter freshAdapter = new RecordingAdapter();
            final ActorRef second = startEntity(freshAdapter, thingId);

            second.tell(command, getRef());
            expectMsgClass(IngestDataPointsResponse.class);
            assertThat(freshAdapter.writtenBatches)
                    .as("recovered actor must not re-write an already-applied batch")
                    .isEmpty();
        }};
    }

    @Test
    public void unappliedBatchIsRetriedAfterRestart() {
        // Mirror durability test: a batch journaled as IngestReceivedEvent but never
        // IngestAppliedEvent (because the adapter failed) must be retried after restart.
        // Proves the journal entry round-trips through MongoDB without loss and the
        // recovery handler rebuilds the inflight map.
        new TestKit(actorSystem) {{
            final RuntimeException backendError = new RuntimeException("mongo unavailable");
            final RecordingAdapter failingAdapter = new RecordingAdapter().failingWith(backendError);
            final ActorRef first = startEntity(failingAdapter, thingId);

            final String corrId = "retry-" + UUID.randomUUID();
            first.tell(IngestDataPoints.of(thingId, sampleBatch(thingId),
                    DittoHeaders.newBuilder().correlationId(corrId).build()), getRef());
            expectMsgClass(Status.Failure.class);
            assertThat(failingAdapter.writtenBatches).hasSize(1);

            watch(first);
            first.tell(PoisonPill.getInstance(), ActorRef.noSender());
            expectTerminated(first);

            final RecordingAdapter healingAdapter = new RecordingAdapter();
            final ActorRef second = startEntity(healingAdapter, thingId);

            // Recovery-driven retry is async; the journal read + adapter call happen after
            // preStart returns. Poll for a generous window — slow because real MongoDB +
            // pekko-persistence cold start.
            within(Duration.ofSeconds(15), () -> {
                while (healingAdapter.writtenBatches.isEmpty()) {
                    try {
                        Thread.sleep(100);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
                return null;
            });
            assertThat(healingAdapter.writtenBatches).hasSize(1);
            assertThat(healingAdapter.writtenBatches.get(0)).hasSize(2);
        }};
    }

    private ActorRef startEntity(final TimeseriesAdapter timeseriesAdapter, final ThingId tid) {
        // Cluster sharding sets the entity actor name to the entityId; we mirror that so
        // persistenceId == "timeseries:" + thingId in both first and second instances.
        return actorSystem.actorOf(TimeseriesIngestActor.propsForTest(timeseriesAdapter), tid.toString());
    }

    private static List<TimeseriesDataPoint> sampleBatch(final ThingId tid) {
        return List.of(
                TimeseriesDataPoint.of(tid, PATH, Instant.parse("2026-01-01T00:00:00Z"),
                        JsonValue.of(21.5), 1L, Collections.emptyMap(), "Cel"),
                TimeseriesDataPoint.of(tid, PATH, Instant.parse("2026-01-01T00:00:30Z"),
                        JsonValue.of(21.6), 2L, Collections.emptyMap(), "Cel"));
    }

    private static Config buildConfig() {
        // Start from the unit-test config (jackson-cbor serialization-bindings + plugin
        // names matching production) and overlay the real MongoDB plugin classes pointed
        // at the test URI. The collection names are namespaced per database so the test
        // collections never collide with a co-located production timeseries journal.
        final Config base = ConfigFactory.load("test.conf");
        final String mongoUriWithDb = mongoUri.endsWith("/")
                ? mongoUri + databaseName
                : mongoUri + "/" + databaseName;

        return ConfigFactory.empty()
                .withValue("pekko.contrib.persistence.mongodb.mongo.mongouri",
                        ConfigValueFactory.fromAnyRef(mongoUriWithDb))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.class",
                        ConfigValueFactory.fromAnyRef("pekko.contrib.persistence.mongodb.MongoJournal"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.plugin-dispatcher",
                        ConfigValueFactory.fromAnyRef("pekko.actor.default-dispatcher"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.overrides.journal-collection",
                        ConfigValueFactory.fromAnyRef("timeseries_journal"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.overrides.journal-index",
                        ConfigValueFactory.fromAnyRef("timeseries_journal_index"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.overrides.realtime-collection",
                        ConfigValueFactory.fromAnyRef("timeseries_realtime"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-journal.overrides.metadata-collection",
                        ConfigValueFactory.fromAnyRef("timeseries_metadata"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-snapshots.class",
                        ConfigValueFactory.fromAnyRef("pekko.contrib.persistence.mongodb.MongoSnapshots"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-snapshots.plugin-dispatcher",
                        ConfigValueFactory.fromAnyRef("pekko.actor.default-dispatcher"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-snapshots.overrides.snaps-collection",
                        ConfigValueFactory.fromAnyRef("timeseries_snaps"))
                .withValue("pekko-contrib-mongodb-persistence-timeseries-snapshots.overrides.snaps-index",
                        ConfigValueFactory.fromAnyRef("timeseries_snaps_index"))
                .withFallback(base)
                .resolve();
    }

    /** Drains a publisher synchronously — used only for the AfterClass DB-drop. */
    private static void blockUntilComplete(final org.reactivestreams.Publisher<?> publisher)
            throws Exception {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<Object>() {

            @Override
            public void onSubscribe(final Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(final Object item) { /* discard */ }
            @Override
            public void onError(final Throwable t) { future.completeExceptionally(t); }
            @Override
            public void onComplete() { future.complete(null); }
        });
        future.get(30, TimeUnit.SECONDS);
    }

    /**
     * Test double for {@link TimeseriesAdapter}: records every batch, optionally fails them.
     * Mirrors the test-fake in {@code TimeseriesIngestActorTest} so the unit + IT layers exercise
     * the actor through the same surface.
     */
    private static final class RecordingAdapter implements TimeseriesAdapter {

        final List<List<TimeseriesDataPoint>> writtenBatches = new ArrayList<>();
        private volatile Throwable cannedFailure = null;

        RecordingAdapter failingWith(final Throwable t) {
            cannedFailure = t;
            return this;
        }

        @Override
        public CompletionStage<Void> initialize(final TimeseriesAdapterConfig config) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public HealthStatus getHealth() {
            return HealthStatus.UP;
        }

        @Override
        public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
            return writeBatch(List.of(dataPoint));
        }

        @Override
        public CompletionStage<Void> writeBatch(final List<TimeseriesDataPoint> dataPoints) {
            synchronized (writtenBatches) {
                writtenBatches.add(new ArrayList<>(dataPoints));
            }
            if (cannedFailure != null) {
                final CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(cannedFailure);
                return future;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
}
