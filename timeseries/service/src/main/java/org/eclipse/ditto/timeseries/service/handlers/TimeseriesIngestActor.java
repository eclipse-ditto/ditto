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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.persistence.RecoveryCompleted;
import org.apache.pekko.persistence.SaveSnapshotFailure;
import org.apache.pekko.persistence.SaveSnapshotSuccess;
import org.apache.pekko.persistence.SnapshotOffer;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.PekkoJacksonCborSerializable;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.persistentactors.AbstractPersistentActorWithTimersAndCleanup;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;

/**
 * Per-Thing sharded <em>persistent</em> entity that journals each {@link IngestDataPoints}
 * batch before writing it to the configured {@link TimeseriesAdapter}, mirroring the
 * shape of {@code ThingPersistenceActor} on the things-service side.
 *
 * <h2>Why persistent</h2>
 * Without a journal, a node crash between {@code adapter.writeBatch} starting and
 * completing silently loses the batch — the publisher's {@code Patterns.ask} times out
 * and retries, but if the publisher itself has crashed too, the data is gone. With a
 * journal, the entity survives node failure: on restart, replay reveals batches that
 * were received but not applied, and the actor retries the MongoDB write itself
 * (no publisher needed). This matches the durability guarantee
 * {@code ThingPersistenceActor} gives Thing entities.
 *
 * <h2>Event sequence per batch</h2>
 * <ol>
 *   <li>Receive {@link IngestDataPoints} command. If the {@code correlation-id} is
 *       already in the in-flight map, treat as a publisher replay — re-trigger the
 *       MongoDB write but don't journal again.</li>
 *   <li>Otherwise persist {@link IngestReceivedEvent} carrying the JSON-encoded batch,
 *       then trigger {@code adapter.writeBatch}.</li>
 *   <li>On write success: persist {@link IngestAppliedEvent} (clears the in-flight
 *       entry), then reply {@link IngestDataPointsResponse} to the original sender.</li>
 *   <li>On write failure: <em>do not</em> journal applied; tell sender
 *       {@link Status.Failure} so its retry timer fires. The in-flight entry stays —
 *       on the next replay (publisher retry or actor restart) we'll try MongoDB again.</li>
 * </ol>
 *
 * <h2>Idempotency</h2>
 * The publisher carries the same {@code correlation-id} across retries. Receiving an
 * already-in-flight correlation-id short-circuits to "re-trigger write only". On
 * recovery from journal, batches without a matching {@code IngestAppliedEvent} are
 * retried against MongoDB. Phase 2 accepts that a crash window between
 * {@code writeBatch} success and {@code IngestAppliedEvent} persist can produce one
 * duplicate row in the time-series collection — Phase 3 closes that with
 * (thingId, path, timestamp) dedup against MongoDB before insert.
 *
 * <h2>Snapshots and passivation</h2>
 * Snapshots are taken every {@value #SNAPSHOT_THRESHOLD} events to keep journal-replay
 * bounded. The actor passivates after {@value #PASSIVATION_TIMEOUT_SECONDS}s of idle —
 * sends {@link ShardRegion.Passivate} to the parent (the shard) so the entity stops and
 * its memory is released. Cluster sharding re-creates the actor on the next message
 * for this Thing.
 */
public final class TimeseriesIngestActor extends AbstractPersistentActorWithTimersAndCleanup {

    /**
     * Thread-safe logger for the read path: the enforcement chain
     * ({@code Patterns.ask(thingsShardRegion, ...)}, {@code policyEnforcerProvider.getPolicyEnforcer(...)},
     * {@code adapter.query(...)}) executes on stages off the actor's mailbox thread, so any log
     * statement reached from one of those callbacks must use a thread-safe logger rather than
     * the inherited single-threaded {@code log} ({@code DittoDiagnosticLoggingAdapter}). The
     * inherited {@code log} is still used for on-actor-thread call sites (receive handlers,
     * recovery, snapshot bookkeeping) where its MDC integration is helpful.
     */
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(TimeseriesIngestActor.class);

    /**
     * Pekko persistence journal plugin id. The matching block in {@code timeseries.conf}
     * declares the MongoDB-backed journal collection.
     */
    public static final String JOURNAL_PLUGIN_ID =
            "pekko-contrib-mongodb-persistence-timeseries-journal";

    /**
     * Pekko persistence snapshot plugin id; snapshots go to the matching
     * {@code timeseries_snaps} collection.
     */
    public static final String SNAPSHOT_PLUGIN_ID =
            "pekko-contrib-mongodb-persistence-timeseries-snapshots";

    /**
     * Take a snapshot every N events. Matches the order-of-magnitude that
     * {@code ThingPersistenceActor}'s default snapshot threshold targets — we'd rather
     * take a few extra snapshots than have replays cross thousands of events on
     * recovery.
     */
    static final int SNAPSHOT_THRESHOLD = 50;

    /**
     * Idle period before the entity passivates. Cluster sharding re-creates it on the
     * next inbound message, so passivation is a memory hint, not a correctness
     * concern.
     */
    static final long PASSIVATION_TIMEOUT_SECONDS = 300;

    private static final Object PASSIVATE_TICK = new Object();

    /**
     * Counter incremented every time {@code adapter.writeBatch} fails for a batch. Visible
     * in the Kamon dashboard as {@code timeseries_ingest_write_failed} so a struggling
     * MongoDB backend (write concern timeouts, replica unavailability) produces a
     * monitorable signal rather than just a WARN log per failure.
     */
    private static final Counter WRITE_FAILURES =
            DittoMetrics.counter("timeseries_ingest_write_failed");

    /**
     * Bound on the in-memory ring of recently-applied correlation-ids. Sized for typical
     * publisher retry windows: the publisher's MAX_ATTEMPTS=3 retries with 5s ask-timeout
     * fits inside ~15s, and a busy entity processes ~hundreds of writes/sec at most. 256
     * entries comfortably covers that without unbounded memory growth.
     */
    private static final int APPLIED_RING_CAPACITY = 256;

    /**
     * Default permission required to read timeseries data when none is configured. Must match the
     * default declared in {@code timeseries.conf}
     * ({@code ditto.timeseries.enforcement.required-permission}).
     */
    public static final String DEFAULT_REQUIRED_PERMISSION = "READ_TS";

    /**
     * Timeout for the {@link SudoRetrieveThing} ask. Kept conservative: this is an in-cluster
     * request to a sharded actor that should respond promptly; longer timeouts only mask issues.
     */
    private static final Duration THING_LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    private final TimeseriesAdapter adapter;
    private final ThingId thingId;

    /** Things-shard proxy used by the read path to resolve the thing's policyId via SudoRetrieveThing. */
    @Nullable private final ActorRef thingsShardRegion;
    /** Provider of the (cached) policy enforcer used to check READ_TS on each requested path. */
    @Nullable private final PolicyEnforcerProvider policyEnforcerProvider;
    /** Permission name verified per path (e.g. "READ_TS" or "READ"). */
    private final String requiredPermission;

    /**
     * In-memory mirror of journaled {@code IngestReceivedEvent} entries that have not
     * yet had a matching {@code IngestAppliedEvent}. Insertion-ordered so on recovery
     * we retry writes in the order they were received.
     */
    private final LinkedHashMap<String, IngestDataPoints> inflight = new LinkedHashMap<>();

    /**
     * Bounded LRU ring of correlation-ids whose batches have been fully applied during
     * the current actor lifetime. Lets us recognise a publisher retry that arrives after
     * we journaled {@code IngestApplied} but the publisher's ask never saw the response
     * (GC pause, network reorder) — without this ring, that retry would re-journal a
     * fresh {@code IngestReceived} and produce a duplicate write to MongoDB Time Series.
     * Phase 2 trade-off: state is in-memory only and lost on actor restart, so a retry
     * crossing a restart can still produce one duplicate; Phase 3 closes that with
     * (thingId, path, timestamp) dedup at the MongoDB layer.
     */
    private final LinkedHashMap<String, Boolean> recentlyApplied =
            new LinkedHashMap<>(APPLIED_RING_CAPACITY, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                    return size() > APPLIED_RING_CAPACITY;
                }
            };

    /**
     * Senders captured during the current actor lifetime so we can reply to live
     * publishers. Never journaled — on recovery this map is empty and we silently retry
     * MongoDB writes without acking.
     */
    private final Map<String, ActorRef> liveSenders = new HashMap<>();

    /**
     * Sequence-number bookkeeping for snapshot scheduling. Bumped on each persist; when
     * it crosses {@link #SNAPSHOT_THRESHOLD} we save a snapshot and reset the
     * counter. Pekko's {@code lastSequenceNr()} would also work but is mutated by
     * snapshot deletion too, which is harder to reason about.
     */
    private long eventsSinceSnapshot = 0;

    /**
     * Latest sequence number confirmed durable by the snapshot store. Required by
     * {@link AbstractPersistentActorWithTimersAndCleanup#getLatestSnapshotSequenceNumber()}
     * so the {@code CleanupPersistence} command can determine which journal entries are
     * safe to delete (everything up to and including this sequence number is covered by
     * the snapshot and can be pruned).
     */
    private long lastSavedSnapshotSequenceNr = 0L;

    @SuppressWarnings("unused")
    private TimeseriesIngestActor(final TimeseriesAdapter adapter,
            @Nullable final ActorRef thingsShardRegion,
            @Nullable final PolicyEnforcerProvider policyEnforcerProvider,
            final String requiredPermission) {
        this.adapter = checkNotNull(adapter, "adapter");
        this.thingsShardRegion = thingsShardRegion;
        this.policyEnforcerProvider = policyEnforcerProvider;
        this.requiredPermission = checkNotNull(requiredPermission, "requiredPermission");
        // The shard-region extractor names entity actors after their entityId (the
        // ThingId). URL-decode because cluster sharding URL-encodes entity names that
        // contain reserved characters.
        this.thingId = ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    /**
     * Test-only {@code Props} variant that skips authorization (read path returns adapter results
     * directly). Package-private so production wiring cannot reach it; production must use
     * {@link #props(TimeseriesAdapter, ActorRef, PolicyEnforcerProvider, String)}, which requires
     * a shard region + enforcer provider and enforces the configured permission per resource path.
     *
     * @param adapter the timeseries adapter shared by all entities on this node.
     * @return the props.
     */
    static Props propsForTest(final TimeseriesAdapter adapter) {
        return Props.create(TimeseriesIngestActor.class, adapter, null, null,
                DEFAULT_REQUIRED_PERMISSION);
    }

    /**
     * Returns Pekko {@code Props} for the full wiring used in production. The same
     * {@link TimeseriesAdapter} instance is shared across all entities on a node — the SPI
     * contract requires thread-safety, so sharing avoids paying for one MongoDB connection
     * pool per entity.
     *
     * @param adapter the timeseries adapter shared by all entities on this node.
     * @param thingsShardRegion proxy actor for the {@code thing} shard region — used to
     * resolve the requested Thing's policy id via {@link SudoRetrieveThing} during read-path
     * authorization.
     * @param policyEnforcerProvider provider that loads the (cached)
     * {@link PolicyEnforcer} for the Thing's policy id.
     * @param requiredPermission the permission name (e.g. {@code READ_TS} or {@code READ})
     * that the requesting subject must hold on every requested resource path.
     * @return the props.
     */
    public static Props props(final TimeseriesAdapter adapter,
            final ActorRef thingsShardRegion,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final String requiredPermission) {
        return Props.create(TimeseriesIngestActor.class,
                adapter,
                checkNotNull(thingsShardRegion, "thingsShardRegion"),
                checkNotNull(policyEnforcerProvider, "policyEnforcerProvider"),
                checkNotNull(requiredPermission, "requiredPermission"));
    }

    @Override
    public String persistenceId() {
        return "timeseries:" + thingId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        timers().startTimerWithFixedDelay(PASSIVATE_TICK, PASSIVATE_TICK,
                Duration.ofSeconds(PASSIVATION_TIMEOUT_SECONDS));
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(IngestReceivedEvent.class, this::recoverReceived)
                .match(IngestAppliedEvent.class, this::recoverApplied)
                .match(SnapshotOffer.class, this::recoverFromSnapshot)
                .match(RecoveryCompleted.class, this::onRecoveryCompleted)
                .build();
    }

    @Override
    public Receive createReceive() {
        // Compose with the parent's Receive so CleanupPersistence (and the
        // delete-snapshots / delete-messages responses it produces) reach the parent's
        // handlers. orElse-pattern is the standard Ditto idiom for layering custom
        // commands on top of the base behaviour.
        return receiveBuilder()
                .match(IngestDataPoints.class, this::handleIngest)
                .match(RetrieveTimeseries.class, this::handleRetrieveTimeseries)
                .match(WriteCompleted.class, this::handleWriteCompleted)
                .match(SaveSnapshotSuccess.class, this::handleSnapshotSuccess)
                .match(SaveSnapshotFailure.class, ssf -> log.warning("Snapshot failed: {}", ssf.cause()))
                .matchEquals(PASSIVATE_TICK, t -> maybePassivate())
                .build()
                .orElse(super.createReceive());
    }

    @Override
    protected long getLatestSnapshotSequenceNumber() {
        return lastSavedSnapshotSequenceNr;
    }

    private void handleSnapshotSuccess(final SaveSnapshotSuccess sss) {
        lastSavedSnapshotSequenceNr = sss.metadata().sequenceNr();
    }

    private void handleIngest(final IngestDataPoints command) {
        final String corrId = correlationIdOf(command);
        if (recentlyApplied.containsKey(corrId)) {
            // Replay arriving AFTER we already journaled IngestApplied — the publisher's
            // ask must have raced with the response (GC pause, network reorder). Don't
            // re-journal and don't re-write; just ack so the publisher's retry queue
            // drains. The bounded recentlyApplied ring covers the typical 15s retry
            // window without unbounded memory growth.
            getSender().tell(IngestDataPointsResponse.of(thingId, command.getDittoHeaders()), getSelf());
            return;
        }
        if (inflight.containsKey(corrId)) {
            // Replay from the publisher's retry path. The journal already records
            // IngestReceivedEvent for this correlation-id; re-trigger the MongoDB
            // write without journaling again. Track the live sender so the eventual
            // ack reaches the retrying publisher.
            liveSenders.put(corrId, getSender());
            triggerWrite(corrId, command);
            return;
        }

        if (command.getDataPoints().isEmpty()) {
            // Empty batch is a no-op — ack without journaling so the journal stays
            // bounded for the empty case (which can happen if the publisher's WoT
            // logic ever produces an empty result list).
            getSender().tell(IngestDataPointsResponse.of(thingId, command.getDittoHeaders()), getSelf());
            return;
        }

        final ActorRef sender = getSender();
        persist(new IngestReceivedEvent(corrId, command.toJsonString()), event -> {
            inflight.put(event.correlationId(), command);
            liveSenders.put(event.correlationId(), sender);
            eventsSinceSnapshot++;
            triggerWrite(event.correlationId(), command);
            maybeSnapshot();
        });
    }

    private void triggerWrite(final String correlationId, final IngestDataPoints command) {
        // Capture the correlation-id locally so the CompletionStage callback doesn't
        // close over actor state (which is forbidden by Ditto's actor-concurrency rules).
        adapter.writeBatch(command.getDataPoints()).whenComplete((ignored, throwable) ->
                getSelf().tell(new WriteCompleted(correlationId, throwable), getSelf()));
    }

    private void handleWriteCompleted(final WriteCompleted msg) {
        final IngestDataPoints command = inflight.get(msg.correlationId());
        if (command == null) {
            // Already applied (e.g. raced with a duplicate handleIngest replay that
            // landed first). Nothing to do.
            return;
        }
        final ActorRef sender = liveSenders.remove(msg.correlationId());
        if (msg.failure() != null) {
            final Throwable cause = unwrap(msg.failure());
            WRITE_FAILURES.increment();
            log.warning("Failed to persist <{}> data points for thing <{}>: {}",
                    command.getDataPoints().size(), thingId, cause.getMessage());
            // Don't journal Applied — leave inflight so a subsequent replay
            // (publisher retry or actor restart) tries again.
            if (sender != null) {
                sender.tell(new Status.Failure(cause), getSelf());
            }
            return;
        }

        persist(new IngestAppliedEvent(msg.correlationId()), event -> {
            inflight.remove(event.correlationId());
            // Mark applied in the LRU ring so a publisher retry that arrives after this
            // ack is dispatched is recognised and ack'd idempotently. The ring is
            // in-memory only — see field javadoc for the Phase 3 follow-up that closes
            // the across-restart duplicate window.
            recentlyApplied.put(event.correlationId(), Boolean.TRUE);
            eventsSinceSnapshot++;
            if (sender != null) {
                sender.tell(IngestDataPointsResponse.of(thingId, command.getDittoHeaders()), getSelf());
            }
            maybeSnapshot();
        });
    }

    private void recoverReceived(final IngestReceivedEvent event) {
        final IngestDataPoints command = parseCommand(event.commandJson());
        if (command != null) {
            inflight.put(event.correlationId(), command);
        }
    }

    private void recoverApplied(final IngestAppliedEvent event) {
        inflight.remove(event.correlationId());
        // Repopulate the LRU ring on recovery too so a retry that arrives shortly after
        // restart is still recognised — within the bounds of the ring's capacity. (Older
        // applied ids are evicted as new ones land; see APPLIED_RING_CAPACITY.)
        recentlyApplied.put(event.correlationId(), Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private void recoverFromSnapshot(final SnapshotOffer offer) {
        if (!(offer.snapshot() instanceof IngestStateSnapshot snap)) {
            log.warning("Unexpected snapshot type <{}>; ignoring", offer.snapshot().getClass());
            return;
        }
        inflight.clear();
        for (final Map.Entry<String, String> entry : snap.inflight().entrySet()) {
            final IngestDataPoints command = parseCommand(entry.getValue());
            if (command != null) {
                inflight.put(entry.getKey(), command);
            }
        }
    }

    private void onRecoveryCompleted(final RecoveryCompleted ignored) {
        if (inflight.isEmpty()) {
            return;
        }
        log.info("Recovery complete; replaying <{}> in-flight batches for thing <{}>",
                inflight.size(), thingId);
        // Senders from before the crash are gone — we silently retry MongoDB writes
        // without acking. The original publisher will have given up by now anyway, but
        // the data still needs to land for chronological completeness.
        for (final Map.Entry<String, IngestDataPoints> entry : inflight.entrySet()) {
            triggerWrite(entry.getKey(), entry.getValue());
        }
    }

    private void maybeSnapshot() {
        if (eventsSinceSnapshot < SNAPSHOT_THRESHOLD) {
            return;
        }
        final Map<String, String> snapInflight = new LinkedHashMap<>();
        for (final Map.Entry<String, IngestDataPoints> e : inflight.entrySet()) {
            snapInflight.put(e.getKey(), e.getValue().toJsonString());
        }
        saveSnapshot(new IngestStateSnapshot(snapInflight));
        eventsSinceSnapshot = 0;
    }

    private void maybePassivate() {
        // Don't passivate while writes are in flight — the WriteCompleted callback
        // would arrive at a stopped actor and end up in dead letters.
        if (inflight.isEmpty()) {
            getContext().getParent().tell(new ShardRegion.Passivate(PoisonPill.getInstance()), getSelf());
        }
    }

    private static String correlationIdOf(final IngestDataPoints command) {
        // Synthetic correlation-id when the publisher (or a test) didn't set one.
        // Unique-per-message so two batches without an id don't collide in the
        // in-flight map.
        return command.getDittoHeaders().getCorrelationId().orElseGet(() -> UUID.randomUUID().toString());
    }

    @Nullable
    private IngestDataPoints parseCommand(final String json) {
        try {
            final JsonObject jsonObject = JsonFactory.newObject(json);
            return IngestDataPoints.fromJson(jsonObject, DittoHeaders.empty());
        } catch (final RuntimeException e) {
            // A corrupt journal entry should not stop the whole entity from recovering.
            // Log and skip — the missing batch is lost, but everything else proceeds.
            log.error(e, "Could not parse journaled IngestDataPoints; skipping. JSON: {}", json);
            return null;
        }
    }

    @Nullable
    private static Throwable unwrap(@Nullable final Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }

    // ---------------------------------------------------------------------------------------------
    // Read path: RetrieveTimeseries
    //
    // Resolve the thing's policyId via SudoRetrieveThing, load the enforcer, verify the configured
    // permission (READ_TS by default) on each requested path, then ask the adapter for the data.
    // Co-locating the read path on the per-Thing sharded entity lets the edge forwarder route via
    // the shard region with AskWithRetryCommandForwarder (same shape as forwardToThings).
    // ---------------------------------------------------------------------------------------------

    private void handleRetrieveTimeseries(final RetrieveTimeseries command) {
        // Capture sender BEFORE any asynchronous call — getSender() inside an async callback
        // returns the wrong reference (or none) once the call completes.
        final ActorRef sender = getSender();
        final CompletionStage<Object> future = authorizeAndQuery(command);
        Patterns.pipe(future, getContext().getDispatcher()).to(sender);
    }

    private CompletionStage<Object> authorizeAndQuery(final RetrieveTimeseries command) {
        // Single error-handling boundary: enforcement or adapter errors both surface through
        // toFailure() — Ditto runtime exceptions go back as themselves so the gateway maps them
        // to the right HTTP envelope, anything else becomes Status.Failure for the publisher.
        final CompletionStage<Object> stage =
                (thingsShardRegion == null || policyEnforcerProvider == null)
                        // Test-only path: no enforcement wired, run the adapter directly.
                        ? runAdapterQuery(command)
                        : enforce(command).thenCompose(allowed -> runAdapterQuery(command));
        return stage.exceptionally(throwable -> toFailure(command, throwable));
    }

    private CompletionStage<Object> runAdapterQuery(final RetrieveTimeseries command) {
        return adapter.query(command.getQuery())
                .thenApply(results -> RetrieveTimeseriesResponse.of(
                        command.getEntityId(), results, command.getDittoHeaders()));
    }

    /**
     * Resolves the Thing's policy id, loads the enforcer, and verifies the configured permission
     * on each requested path. Failures propagate as either
     * {@link ThingNotAccessibleException} (Thing missing or path denied — 404-not-403) or the
     * original cause for transport / infra errors.
     */
    private CompletionStage<Void> enforce(final RetrieveTimeseries command) {
        final ThingId queryThingId = command.getEntityId();
        final DittoHeaders headers = command.getDittoHeaders();
        final SudoRetrieveThing sudo = SudoRetrieveThing.of(queryThingId, headers);
        return Patterns.ask(thingsShardRegion, sudo, THING_LOOKUP_TIMEOUT)
                .thenCompose(reply -> resolveSudoReply(reply, queryThingId, headers))
                .thenCompose(thing -> loadEnforcer(thing, queryThingId, headers))
                .thenAccept(enforcerWithContext -> verifyPaths(enforcerWithContext, command));
    }

    private CompletionStage<Thing> resolveSudoReply(final Object reply, final ThingId queryThingId,
            final DittoHeaders headers) {
        if (reply instanceof SudoRetrieveThingResponse response) {
            return CompletableFuture.completedFuture(response.getThing());
        }
        if (reply instanceof DittoRuntimeException dre) {
            return CompletableFuture.failedFuture(dre);
        }
        if (reply instanceof Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
        LOGGER.withCorrelationId(headers)
                .warn("Unexpected reply <{}> for SudoRetrieveThing of <{}>; treating as not-found.",
                        reply, queryThingId);
        return CompletableFuture.failedFuture(
                ThingNotAccessibleException.newBuilder(queryThingId).dittoHeaders(headers).build());
    }

    private CompletionStage<EnforcerWithContext> loadEnforcer(final Thing thing,
            final ThingId queryThingId,
            final DittoHeaders headers) {
        final Optional<PolicyId> policyIdOpt = thing.getPolicyId();
        if (policyIdOpt.isEmpty()) {
            // A Thing without a policy is a configuration anomaly; deny access in line with the
            // 404-not-403 discipline rather than leaking an internal error.
            LOGGER.withCorrelationId(headers)
                    .warn("Thing <{}> has no policyId; denying timeseries access.", queryThingId);
            return CompletableFuture.failedFuture(
                    ThingNotAccessibleException.newBuilder(queryThingId).dittoHeaders(headers).build());
        }
        final PolicyId policyId = policyIdOpt.get();
        return policyEnforcerProvider.getPolicyEnforcer(policyId)
                .thenCompose(opt -> opt
                        .<CompletionStage<EnforcerWithContext>>map(pe ->
                                CompletableFuture.completedFuture(
                                        new EnforcerWithContext(pe.getEnforcer(),
                                                headers.getAuthorizationContext())))
                        .orElseGet(() -> {
                            LOGGER.withCorrelationId(headers)
                                    .warn("PolicyEnforcer for policy <{}> on thing <{}> could not " +
                                            "be loaded; denying timeseries access.", policyId, queryThingId);
                            return CompletableFuture.failedFuture(
                                    ThingNotAccessibleException.newBuilder(queryThingId)
                                            .dittoHeaders(headers).build());
                        }));
    }

    private void verifyPaths(final EnforcerWithContext enforcerWithContext,
            final RetrieveTimeseries command) {
        final Permissions required = Permissions.newInstance(requiredPermission);
        final List<JsonPointer> paths = command.getQuery().getPaths();
        for (final JsonPointer path : paths) {
            final ResourceKey resourceKey = PoliciesResourceType.thingResource(path);
            final boolean granted = enforcerWithContext.enforcer.hasUnrestrictedPermissions(
                    resourceKey, enforcerWithContext.authorizationContext, required);
            if (!granted) {
                LOGGER.withCorrelationId(command.getDittoHeaders())
                        .info("Subject <{}> denied <{}> on <{}> for thing <{}>.",
                                enforcerWithContext.authorizationContext.getAuthorizationSubjectIds(),
                                requiredPermission, path, command.getEntityId());
                throw ThingNotAccessibleException.newBuilder(command.getEntityId())
                        .dittoHeaders(command.getDittoHeaders())
                        .build();
            }
        }
    }

    private Object toFailure(final RetrieveTimeseries command, final Throwable throwable) {
        final Throwable cause = unwrap(throwable);
        LOGGER.withCorrelationId(command.getDittoHeaders())
                .warn("RetrieveTimeseries for thing <{}> failed: {}",
                        command.getEntityId(), cause.getMessage());
        // Ditto convention: surface DittoRuntimeException directly so gateway maps it to an HTTP
        // error envelope; non-Ditto throwables fall back to Status.Failure for visibility.
        if (cause instanceof DittoRuntimeException dre) {
            return dre.setDittoHeaders(command.getDittoHeaders());
        }
        return new Status.Failure(cause);
    }

    /**
     * Pair of loaded {@link Enforcer} with the originating {@link AuthorizationContext}, carried
     * through the enforcement pipeline so the per-path permission check has both pieces in scope
     * without re-fetching headers.
     */
    private static final class EnforcerWithContext {

        private final Enforcer enforcer;
        private final AuthorizationContext authorizationContext;

        EnforcerWithContext(final Enforcer enforcer, final AuthorizationContext authorizationContext) {
            this.enforcer = enforcer;
            this.authorizationContext = authorizationContext;
        }
    }

    /**
     * Internal callback message piped from the {@link TimeseriesAdapter}'s
     * {@link java.util.concurrent.CompletionStage} back to the actor thread.
     */
    private record WriteCompleted(String correlationId, @Nullable Throwable failure) {}

    /**
     * Journal event recording that an {@link IngestDataPoints} batch was received and
     * is pending durable write. Carries the JSON-stringified command (rather than the
     * command object directly) so Jackson CBOR serialization stays straightforward —
     * Signal subclasses don't have Jackson annotations and the JSON representation is
     * the canonical wire format anyway.
     */
    public record IngestReceivedEvent(String correlationId, String commandJson)
            implements PekkoJacksonCborSerializable {}

    /**
     * Journal event recording that the batch with the given correlation-id has been
     * successfully written to MongoDB Time Series. The pair {@code IngestReceivedEvent}
     * + {@code IngestAppliedEvent} marks a batch's lifecycle in the journal; absence of
     * the second signals "retry me on recovery".
     */
    public record IngestAppliedEvent(String correlationId) implements PekkoJacksonCborSerializable {}

    /**
     * Snapshot payload — the in-flight map serialized as
     * {@code correlationId -> commandJson}. Restoring snaps reconstructs the in-memory
     * state without replaying journal events that are older than the snapshot.
     */
    public record IngestStateSnapshot(Map<String, String> inflight)
            implements PekkoJacksonCborSerializable {}
}
