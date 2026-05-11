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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for the persistent sharded {@link TimeseriesIngestActor}. The actor's
 * lifecycle is non-trivial (journal-then-write-then-journal-applied), so each test
 * exercises one slice of the contract.
 * <p>
 * Persistence uses an in-memory journal (see {@code test.conf}) keyed by the
 * persistenceId, which means restarting the actor under the same name within one
 * {@link ActorSystem} replays its journal. Tests that exercise replay use this
 * property by stopping the actor with a {@code PoisonPill} and starting a new one
 * pointing at the same entity name.
 */
public final class TimeseriesIngestActorTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");

    private static ActorSystem actorSystem;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create("TimeseriesIngestActorTest",
                ConfigFactory.load("test.conf"));
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void successfulBatchProducesAck() {
        new TestKit(actorSystem) {{
            final RecordingAdapter adapter = new RecordingAdapter();
            final ActorRef ingest = startEntity(adapter, THING_ID);

            final IngestDataPoints command = IngestDataPoints.of(THING_ID, sampleBatch(THING_ID),
                    DittoHeaders.newBuilder().correlationId("ack-1").build());
            ingest.tell(command, getRef());

            final IngestDataPointsResponse ack = expectMsgClass(IngestDataPointsResponse.class);
            assertThat((Object) ack.getEntityId()).isEqualTo(THING_ID);
            assertThat(ack.getDittoHeaders().getCorrelationId()).contains("ack-1");
            assertThat(adapter.writtenBatches).hasSize(1);
            assertThat(adapter.writtenBatches.get(0)).hasSize(2);
        }};
    }

    @Test
    public void emptyBatchAcksWithoutWriting() {
        // The actor's contract: empty batches ack without journaling and without
        // calling the adapter, so the journal stays bounded for the empty case (which
        // can happen if the publisher's WoT logic ever yields an empty list).
        new TestKit(actorSystem) {{
            final RecordingAdapter adapter = new RecordingAdapter();
            final ActorRef ingest = startEntity(adapter, ThingId.of("org.eclipse.ditto", "empty-1"));

            ingest.tell(IngestDataPoints.of(ThingId.of("org.eclipse.ditto", "empty-1"),
                    Collections.emptyList(),
                    DittoHeaders.newBuilder().correlationId("empty").build()), getRef());

            final IngestDataPointsResponse ack = expectMsgClass(IngestDataPointsResponse.class);
            assertThat(ack.getDittoHeaders().getCorrelationId()).contains("empty");
            assertThat(adapter.writtenBatches).isEmpty();
        }};
    }

    @Test
    public void adapterFailureSurfacesAsStatusFailureWithoutJournalingApplied() {
        // On adapter failure the actor must NOT journal IngestApplied — that's what
        // lets recovery retry the batch. We can't directly observe the journal here,
        // but we can observe (a) the publisher receives Status.Failure (its retry
        // signal), and (b) a follow-up retry with the same correlation-id triggers
        // another adapter call (proving inflight state was preserved).
        new TestKit(actorSystem) {{
            final RuntimeException backendError = new RuntimeException("mongo unavailable");
            final RecordingAdapter adapter = new RecordingAdapter().failingWith(backendError);
            final ThingId tid = ThingId.of("org.eclipse.ditto", "failing-1");
            final ActorRef ingest = startEntity(adapter, tid);

            final String corrId = UUID.randomUUID().toString();
            ingest.tell(IngestDataPoints.of(tid, sampleBatch(tid),
                    DittoHeaders.newBuilder().correlationId(corrId).build()), getRef());
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause().getMessage()).contains("mongo unavailable");
            assertThat(adapter.writtenBatches).hasSize(1);

            // Now simulate the publisher's retry — same correlation-id. The actor
            // recognises it as inflight (journaled but not applied) and re-issues the
            // adapter call rather than re-journaling.
            adapter.clearFailure();
            ingest.tell(IngestDataPoints.of(tid, sampleBatch(tid),
                    DittoHeaders.newBuilder().correlationId(corrId).build()), getRef());
            expectMsgClass(IngestDataPointsResponse.class);
            assertThat(adapter.writtenBatches).hasSize(2);
        }};
    }

    @Test
    public void retryAfterApplyIsAckedIdempotentlyWithoutRewriting() {
        // The ack-lost-in-transit dedup window: first batch is fully applied (we get
        // the ack), then the publisher re-sends with the same correlation-id (because
        // its Patterns.ask raced with a GC pause / network reorder). The actor must
        // recognise the replay against its bounded recently-applied ring and ack
        // idempotently — re-journaling and re-writing would corrupt the timeseries
        // collection with a duplicate row.
        new TestKit(actorSystem) {{
            final RecordingAdapter adapter = new RecordingAdapter();
            final ThingId tid = ThingId.of("org.eclipse.ditto", "applied-retry-1");
            final ActorRef ingest = startEntity(adapter, tid);

            final String corrId = "applied-retry-" + UUID.randomUUID();
            final IngestDataPoints command = IngestDataPoints.of(tid, sampleBatch(tid),
                    DittoHeaders.newBuilder().correlationId(corrId).build());

            ingest.tell(command, getRef());
            expectMsgClass(IngestDataPointsResponse.class);
            assertThat(adapter.writtenBatches).hasSize(1);

            // Same correlation-id, same batch — the in-memory ring should ack without
            // a second adapter call.
            ingest.tell(command, getRef());
            expectMsgClass(IngestDataPointsResponse.class);
            assertThat(adapter.writtenBatches).hasSize(1);
        }};
    }

    @Test
    public void replayFromJournalRetriesUnappliedBatches() {
        // The core durability test: a batch is journaled (IngestReceived) but not
        // applied (because adapter failed). On actor restart, the journal replay
        // rebuilds the inflight map and the actor retries the adapter call without
        // any external trigger.
        new TestKit(actorSystem) {{
            final ThingId tid = ThingId.of("org.eclipse.ditto", "replay-1");
            final RecordingAdapter failingAdapter = new RecordingAdapter()
                    .failingWith(new RuntimeException("transient"));
            final ActorRef firstInstance = startEntity(failingAdapter, tid);

            final String corrId = "replay-test-" + UUID.randomUUID();
            firstInstance.tell(IngestDataPoints.of(tid, sampleBatch(tid),
                    DittoHeaders.newBuilder().correlationId(corrId).build()), getRef());
            expectMsgClass(Status.Failure.class);
            assertThat(failingAdapter.writtenBatches).hasSize(1);

            // Stop the first instance — its in-memory state is gone but the journal
            // entries persist within the test ActorSystem's in-memory plugin.
            watch(firstInstance);
            firstInstance.tell(org.apache.pekko.actor.PoisonPill.getInstance(), ActorRef.noSender());
            expectTerminated(firstInstance);

            // Bring up a new instance (same entity name → same persistenceId) with a
            // working adapter. Recovery should retry the unapplied batch.
            final RecordingAdapter healingAdapter = new RecordingAdapter();
            final ActorRef secondInstance = startEntity(healingAdapter, tid);

            // No external message; we just wait for the recovery-driven retry to land.
            // Generous window because recovery + journal read + adapter call are all async.
            within(java.time.Duration.ofSeconds(5), () -> {
                while (healingAdapter.writtenBatches.isEmpty()) {
                    try {
                        Thread.sleep(50);
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

    @Test
    public void retrieveTimeseriesReturnsAdapterResults() {
        // The merged actor handles RetrieveTimeseries directly (no separate query-handler).
        // The test-only Props variant skips enforcement, so the response is whatever the
        // adapter's query() returns. RecordingAdapter returns an empty list — we assert the
        // response is shaped correctly and reaches the sender.
        new TestKit(actorSystem) {{
            final RecordingAdapter adapter = new RecordingAdapter();
            final ThingId tid = ThingId.of("org.eclipse.ditto", "query-1");
            final ActorRef ingest = startEntity(adapter, tid);

            final org.eclipse.ditto.timeseries.model.TimeseriesQuery query =
                    org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(tid,
                            Collections.singletonList(PATH),
                            Instant.parse("2026-01-01T00:00:00Z"),
                            Instant.parse("2026-01-02T00:00:00Z"));
            final org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries cmd =
                    org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries.of(query,
                            DittoHeaders.newBuilder().correlationId("query-test").build());

            ingest.tell(cmd, getRef());
            final org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse response =
                    expectMsgClass(org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse.class);
            assertThat((Object) response.getEntityId()).isEqualTo(tid);
            assertThat(response.getResults()).isEmpty();
        }};
    }

    @Test
    public void retrieveTimeseriesSurfacesAdapterFailureAsStatusFailure() {
        // Adapter throwing on query() must surface as Status.Failure — the gateway then maps
        // that to a 5XX rather than the success envelope.
        new TestKit(actorSystem) {{
            final RuntimeException backendError = new RuntimeException("mongo unavailable");
            final RecordingAdapter adapter = new RecordingAdapter().failingQueryWith(backendError);
            final ThingId tid = ThingId.of("org.eclipse.ditto", "query-fail-1");
            final ActorRef ingest = startEntity(adapter, tid);

            final org.eclipse.ditto.timeseries.model.TimeseriesQuery query =
                    org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(tid,
                            Collections.singletonList(PATH),
                            Instant.parse("2026-01-01T00:00:00Z"),
                            Instant.parse("2026-01-02T00:00:00Z"));
            final org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries cmd =
                    org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries.of(query,
                            DittoHeaders.newBuilder().correlationId("query-fail-test").build());

            ingest.tell(cmd, getRef());
            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause().getMessage()).contains("mongo unavailable");
        }};
    }

    @Test
    public void replayDoesNotRetryAlreadyAppliedBatches() {
        // Symmetric to the previous test: when the journal contains both
        // IngestReceived and IngestApplied for a batch, recovery removes it from
        // inflight and does NOT re-call the adapter. This is what bounds duplicate
        // writes — without IngestApplied bookkeeping, every restart would re-write
        // the entire journal.
        new TestKit(actorSystem) {{
            final ThingId tid = ThingId.of("org.eclipse.ditto", "applied-1");
            final RecordingAdapter firstAdapter = new RecordingAdapter();
            final ActorRef firstInstance = startEntity(firstAdapter, tid);

            firstInstance.tell(IngestDataPoints.of(tid, sampleBatch(tid),
                    DittoHeaders.newBuilder().correlationId("done").build()), getRef());
            expectMsgClass(IngestDataPointsResponse.class);

            watch(firstInstance);
            firstInstance.tell(org.apache.pekko.actor.PoisonPill.getInstance(), ActorRef.noSender());
            expectTerminated(firstInstance);

            // Restart with a new adapter. Recovery should NOT call this adapter for
            // the already-applied batch.
            final RecordingAdapter secondAdapter = new RecordingAdapter();
            final ActorRef secondInstance = startEntity(secondAdapter, tid);

            // Brief settle then assert. expectNoMessage on our test ref doubles as a
            // wait window (we don't expect any reply back at this ref because the
            // recovery doesn't ack).
            expectNoMessage(scala.concurrent.duration.Duration.create(500, TimeUnit.MILLISECONDS));
            assertThat(secondAdapter.writtenBatches).isEmpty();
        }};
    }

    private ActorRef startEntity(final TimeseriesAdapter adapter, final ThingId thingId) {
        // Cluster sharding sets the entity actor name to the entityId. We mimic that
        // for tests so persistenceId is determined by the actor name.
        return actorSystem.actorOf(TimeseriesIngestActor.propsForTest(adapter), thingId.toString());
    }

    private static List<TimeseriesDataPoint> sampleBatch(final ThingId tid) {
        return List.of(
                TimeseriesDataPoint.of(tid, PATH, Instant.parse("2026-01-01T00:00:00Z"),
                        JsonValue.of(21.5), 1L, Collections.emptyMap(), "Cel"),
                TimeseriesDataPoint.of(tid, PATH, Instant.parse("2026-01-01T00:00:30Z"),
                        JsonValue.of(21.6), 2L, Collections.emptyMap(), "Cel"));
    }

    /**
     * Test double for {@link TimeseriesAdapter}. Records every batch and lets the test
     * seed (and clear) a synthetic backend failure for the failure-then-recover paths.
     */
    private static final class RecordingAdapter implements TimeseriesAdapter {

        final List<List<TimeseriesDataPoint>> writtenBatches = new ArrayList<>();
        private volatile Throwable cannedFailure = null;
        private volatile Throwable cannedQueryFailure = null;

        RecordingAdapter failingWith(final Throwable t) {
            cannedFailure = t;
            return this;
        }

        RecordingAdapter failingQueryWith(final Throwable t) {
            cannedQueryFailure = t;
            return this;
        }

        void clearFailure() {
            cannedFailure = null;
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
            if (cannedQueryFailure != null) {
                final CompletableFuture<List<TimeseriesQueryResult>> future = new CompletableFuture<>();
                future.completeExceptionally(cannedQueryFailure);
                return future;
            }
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
    }
}
