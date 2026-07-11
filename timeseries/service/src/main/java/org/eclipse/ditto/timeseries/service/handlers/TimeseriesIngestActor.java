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

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.cluster.sharding.ShardRegion;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Permissions;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesQueryPlanner;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseries;
import org.eclipse.ditto.timeseries.model.signals.commands.RetrieveTimeseriesResponse;

/**
 * Per-Thing sharded entity that forwards each {@link IngestDataPoints} batch to the
 * configured {@link TimeseriesAdapter} and serves {@link RetrieveTimeseries} reads for
 * the same Thing.
 *
 * <h2>No Pekko Persistence</h2>
 * Unlike {@code ThingPersistenceActor}, this actor has no entity state that evolves over
 * events: the durable truth is the MongoDB Time Series collection itself. The publisher
 * ({@code TimeseriesIngestPublisher}) already retries failed batches with bounded
 * {@code MAX_ATTEMPTS}, which covers the same crash window an event-sourced journal
 * would have protected. Pekko Persistence was therefore intentionally dropped to avoid
 * paying an extra MongoDB write per batch plus snapshot churn for redundant protection.
 *
 * <h2>Write path</h2>
 * <ol>
 *   <li>Receive {@link IngestDataPoints}. If empty, ack immediately without calling the
 *       adapter.</li>
 *   <li>If the {@code correlation-id} is in the {@link #recentlyApplied} ring, the batch
 *       was already written; ack idempotently (covers the race where the publisher's
 *       ask times out at 5s but our adapter write completed in the same tick).</li>
 *   <li>If the {@code correlation-id} is already in {@link #liveSenders}, a write is
 *       in flight for that batch; replace the sender reference so the eventual reply
 *       reaches the most recent retry, but do not start a second write.</li>
 *   <li>Otherwise register the sender and call {@code adapter.writeBatch}.</li>
 *   <li>On {@code writeBatch} success: ack the sender, record the correlation-id in
 *       {@link #recentlyApplied}, and clear the live-sender entry.</li>
 *   <li>On {@code writeBatch} failure: clear the live-sender entry and reply with
 *       {@link Status.Failure}; the publisher's retry timer fires and re-asks with the
 *       same correlation-id.</li>
 * </ol>
 *
 * <h2>Idempotency window</h2>
 * The bounded {@link #recentlyApplied} LRU covers the duplicate-on-success window only
 * within the current actor lifetime. After passivation or actor restart the ring is
 * empty, so a duplicate retry that lands across a restart can produce one duplicate row
 * in the time-series collection. A later phase closes that gap with
 * {@code (thingId, path, timestamp)} dedup inside the MongoDB adapter.
 *
 * <h2>Read path</h2>
 * Co-located on the same per-Thing entity so the edge forwarder can route
 * {@link RetrieveTimeseries} via the timeseries shard region with
 * {@code AskWithRetryCommandForwarder} (same shape as {@code forwardToThings}). The
 * read path resolves the Thing's policy id via {@link SudoRetrieveThing}, loads the
 * cached enforcer, verifies the configured permission on every requested path, then
 * asks the adapter for results. Authorization failures surface as
 * {@link ThingNotAccessibleException} (404-not-403).
 *
 * <h2>Passivation</h2>
 * The actor passivates after {@value #PASSIVATION_TIMEOUT_SECONDS}s of idle (no
 * in-flight writes). Cluster sharding re-creates it on the next message for this
 * Thing.
 */
public final class TimeseriesIngestActor extends AbstractActorWithTimers {

    /**
     * Thread-safe logger for the read path: the enforcement chain
     * ({@code Patterns.ask(thingsShardRegion, ...)}, {@code policyEnforcerProvider.getPolicyEnforcer(...)},
     * {@code adapter.query(...)}) executes on stages off the actor's mailbox thread, so any log
     * statement reached from one of those callbacks must use a thread-safe logger rather than
     * the inherited single-threaded {@code log} ({@code DittoDiagnosticLoggingAdapter}). The
     * inherited {@code log} is still used for on-actor-thread call sites (receive handlers)
     * where its MDC integration is helpful.
     */
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(TimeseriesIngestActor.class);

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
     * publisher retry windows: with MAX_ATTEMPTS=3 retries × 5 s ask-timeout the publisher
     * can re-ask up to ~15 s after the original send. At 1024 entries the ring covers ~68
     * sustained events/sec for that whole window — enough headroom for a single Thing
     * pushing ~50 properties/sec each at 1 Hz, which exceeds realistic IoT workloads. The
     * memory cost is bounded: ~1024 × (UUID-string + Boolean) ≈ ~100 KB per entity.
     * <p>
     * If an entity sustains a higher rate than that, retries arriving after the entry has
     * been evicted fall through to a fresh {@code triggerWrite}, producing one duplicate
     * row in MongoDB for the affected batch. Cross-passivation duplicates are a separate
     * concern, addressed by adapter-side {@code (thingId, path, timestamp)} dedup in a
     * later phase.
     */
    private static final int APPLIED_RING_CAPACITY = 1024;

    /**
     * Timeout for the {@link SudoRetrieveThing} ask. Kept conservative: this is an in-cluster
     * request to a sharded actor that should respond promptly; longer timeouts only mask issues.
     */
    private static final Duration THING_LOOKUP_TIMEOUT = Duration.ofSeconds(5);

    private final DittoDiagnosticLoggingAdapter log =
            DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final TimeseriesAdapter adapter;
    private final TimeseriesQueryPlanner queryPlanner;
    private final ThingId thingId;

    /** Things-shard proxy used by the read path to resolve the thing's policyId via SudoRetrieveThing. */
    @Nullable private final ActorRef thingsShardRegion;
    /** Provider of the (cached) policy enforcer used to check the configured read-permission. */
    @Nullable private final PolicyEnforcerProvider policyEnforcerProvider;
    /**
     * When {@code false} (default, strict): the per-path enforcement check requires
     * {@link Permission#READ_TS}. When {@code true} (simplified mode): the check requires
     * {@link Permission#READ} on the resource instead. Two-mode contract — see
     * {@code TimeseriesRootActor.SIMPLIFIED_READ_PERMISSION_CONFIG_PATH}.
     */
    private final boolean simplifiedReadPermission;

    /**
     * Senders awaiting acks for in-flight adapter writes, keyed by correlation-id. A
     * publisher retry that arrives while a write is still in flight replaces the entry
     * (most recent sender wins) rather than triggering a second write.
     */
    private final Map<String, ActorRef> liveSenders = new HashMap<>();

    /**
     * Bounded LRU of correlation-ids whose batches have been successfully written during
     * the current actor lifetime. Covers the duplicate-on-success race where the
     * publisher's {@code Patterns.ask} times out before our reply arrives, the publisher
     * retries with the same id, and we'd otherwise issue a second {@code writeBatch}. The
     * ring is in-memory only — across passivation / restart this protection is lost; a
     * later adapter-side {@code (thingId, path, timestamp)} dedup closes that window.
     */
    private final LinkedHashMap<String, Boolean> recentlyApplied =
            new LinkedHashMap<>(APPLIED_RING_CAPACITY + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                    return size() > APPLIED_RING_CAPACITY;
                }
            };

    @SuppressWarnings("unused")
    private TimeseriesIngestActor(final TimeseriesAdapter adapter,
            @Nullable final ActorRef thingsShardRegion,
            @Nullable final PolicyEnforcerProvider policyEnforcerProvider,
            final boolean simplifiedReadPermission) {
        this.adapter = checkNotNull(adapter, "adapter");
        this.queryPlanner = new TimeseriesQueryPlanner(adapter);
        this.thingsShardRegion = thingsShardRegion;
        this.policyEnforcerProvider = policyEnforcerProvider;
        this.simplifiedReadPermission = simplifiedReadPermission;
        // The shard-region extractor names entity actors after their entityId (the
        // ThingId). URL-decode because cluster sharding URL-encodes entity names that
        // contain reserved characters.
        this.thingId = ThingId.of(URLDecoder.decode(getSelf().path().name(), StandardCharsets.UTF_8));
    }

    /**
     * Test-only {@code Props} variant that skips authorization (read path returns adapter results
     * directly). Package-private so production wiring cannot reach it; production must use
     * {@link #props(TimeseriesAdapter, ActorRef, PolicyEnforcerProvider, boolean)}, which requires
     * a shard region + enforcer provider and enforces the configured read permission per resource
     * path.
     *
     * @param adapter the timeseries adapter shared by all entities on this node.
     * @return the props.
     */
    static Props propsForTest(final TimeseriesAdapter adapter) {
        return Props.create(TimeseriesIngestActor.class, adapter, null, null, false);
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
     * @param simplifiedReadPermission when {@code false} the per-path enforcement check requires
     * {@link Permission#READ_TS}; when {@code true} the check requires {@link Permission#READ}.
     * @return the props.
     */
    public static Props props(final TimeseriesAdapter adapter,
            final ActorRef thingsShardRegion,
            final PolicyEnforcerProvider policyEnforcerProvider,
            final boolean simplifiedReadPermission) {
        return Props.create(TimeseriesIngestActor.class,
                adapter,
                checkNotNull(thingsShardRegion, "thingsShardRegion"),
                checkNotNull(policyEnforcerProvider, "policyEnforcerProvider"),
                simplifiedReadPermission);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        getTimers().startTimerWithFixedDelay(PASSIVATE_TICK, PASSIVATE_TICK,
                Duration.ofSeconds(PASSIVATION_TIMEOUT_SECONDS));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(IngestDataPoints.class, this::handleIngest)
                .match(RetrieveTimeseries.class, this::handleRetrieveTimeseries)
                .match(WriteCompleted.class, this::handleWriteCompleted)
                .matchEquals(PASSIVATE_TICK, t -> maybePassivate())
                .build();
    }

    private void handleIngest(final IngestDataPoints command) {
        if (command.getDataPoints().isEmpty()) {
            // Empty batch is a no-op — ack without touching the adapter (can happen if
            // the publisher's WoT logic produces an empty result list).
            getSender().tell(IngestDataPointsResponse.of(thingId, command.getDittoHeaders()), getSelf());
            return;
        }

        final String corrId = correlationIdOf(command);
        if (recentlyApplied.containsKey(corrId)) {
            // Duplicate retry landing after we already wrote + acked this batch — the
            // publisher's ask must have raced with the response. Ack idempotently
            // without re-writing.
            getSender().tell(IngestDataPointsResponse.of(thingId, command.getDittoHeaders()), getSelf());
            return;
        }

        if (liveSenders.containsKey(corrId)) {
            // Retry while the original write is still in flight — keep one writeBatch
            // call open and ack the most recent retry when it completes.
            liveSenders.put(corrId, getSender());
            return;
        }

        liveSenders.put(corrId, getSender());
        triggerWrite(corrId, command);
    }

    private void triggerWrite(final String correlationId, final IngestDataPoints command) {
        // Capture the correlation-id locally so the CompletionStage callback doesn't
        // close over actor state (which is forbidden by Ditto's actor-concurrency rules).
        adapter.writeBatch(command.getDataPoints()).whenComplete((ignored, throwable) ->
                getSelf().tell(new WriteCompleted(correlationId, command.getDittoHeaders(), throwable),
                        getSelf()));
    }

    private void handleWriteCompleted(final WriteCompleted msg) {
        final ActorRef sender = liveSenders.remove(msg.correlationId());
        if (msg.failure() != null) {
            final Throwable cause = unwrap(msg.failure());
            WRITE_FAILURES.increment();
            log.warning("Failed to persist data points for thing <{}>: {}",
                    thingId, cause.getMessage());
            if (sender != null) {
                sender.tell(new Status.Failure(cause), getSelf());
            }
            return;
        }
        recentlyApplied.put(msg.correlationId(), Boolean.TRUE);
        if (sender != null) {
            sender.tell(IngestDataPointsResponse.of(thingId, msg.headers()), getSelf());
        }
    }

    private void maybePassivate() {
        // Don't passivate while writes are in flight — the WriteCompleted callback
        // would arrive at a stopped actor and end up in dead letters.
        if (liveSenders.isEmpty()) {
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
        // Execute through the planner: for a backend whose capabilities declare a complete native
        // query (MongoDB) this delegates straight to the adapter (transparent); a scan-only backend
        // is driven via scan(...) + the compute kernel. Either way the results are identical.
        return queryPlanner.execute(command.getQuery())
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
        // Two-mode contract per `simplifiedReadPermission`: strict (default) checks READ_TS;
        // simplified checks READ. The selection is fixed for the lifetime of this entity (it's
        // a constructor-injected boolean) so there's no per-request branching surprise.
        final String requiredPermission =
                simplifiedReadPermission ? Permission.READ : Permission.READ_TS;
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
    private record WriteCompleted(String correlationId, DittoHeaders headers,
                                  @Nullable Throwable failure) {}
}
