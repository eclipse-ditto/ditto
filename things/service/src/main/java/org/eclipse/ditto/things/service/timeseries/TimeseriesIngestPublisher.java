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
package org.eclipse.ditto.things.service.timeseries;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import javax.annotation.Nullable;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.timeseries.ThingEventLeafExtractor.PropertyLeaf;
import org.eclipse.ditto.things.service.timeseries.WotLeafResolver.ResolvedLeaf;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.WotTimeseriesAnnotation;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Per-Things-service-node actor that turns {@link ThingEvent}s into {@link IngestDataPoints}
 * batches and asks the Timeseries shard region with bounded retries until the persistent entity
 * acks.
 * <p>
 * Each event is decomposed into the scalar feature-property leaves it changed
 * ({@link ThingEventLeafExtractor}), and every leaf whose WoT schema carries a
 * {@code ditto:timeseries} annotation with {@code ingest = ALL} ({@link WotLeafResolver}) becomes
 * one {@link TimeseriesDataPoint}. All points produced from one event ship in a single batch. The
 * annotation may sit on a nested property (e.g. {@code flowTemperature/temperature}), so a Thing
 * can opt a single scalar into ingest without dragging in sibling fields (e.g. an
 * {@code updatedAt} timestamp). This actor is opt-in: a Thing without any matching annotation
 * produces no points and no traffic.
 *
 * <h2>Delivery model</h2>
 * Best-effort at-least-once with a bounded retry budget. Two crash domains:
 * <ul>
 *   <li><b>Timeseries-side crashes</b> (entity host node, MongoDB unavailable, network partition
 *   between Things and Timeseries) are covered by the retry loop: up to {@link #MAX_ATTEMPTS}
 *   attempts with the same correlation-id, which the receiving entity uses to recognise replays
 *   and avoid duplicate writes (`recentlyApplied` LRU on the entity side).</li>
 *   <li><b>Things-side crashes</b> between {@code ThingPersistenceActor.publishEvent} firing the
 *   {@link IngestRequest} and the publisher's ask completing are <em>not</em> covered. The
 *   publisher is non-persistent; mailbox, in-flight WoT-resolution stages and in-flight asks are
 *   in-memory only. A Things-service JVM crash in that window loses the affected batches. The
 *   Things journal records the originating event durably, but {@code publishEvent} is a
 *   post-persist hook that only fires for new events going forward — recovery does not re-fire
 *   it. Closing this gap would require a persistent local publisher journal or a Timeseries-side
 *   replay against {@code MongoReadJournal}; neither is in Phase 1 scope, and the IoT
 *   property-stream use case tolerates the resulting occasional gaps.</li>
 * </ul>
 * The receiving entity is intentionally non-persistent (see {@code TimeseriesIngestActor}
 * Javadoc, "No Pekko Persistence" section): the MongoDB Time Series collection is the durable
 * truth, and this publisher's retry already covers the Timeseries-side crash window the journal
 * would have protected.
 *
 * <h2>WoT resolution</h2>
 * {@link WotThingModelResolver} caches resolved ThingModels and submodel maps internally, so
 * steady-state updates hit the cache; cold lookups pay one HTTP fetch per submodel. Resolution
 * is composed with {@code thenCompose} on the WoT executor — the actor thread never blocks.
 *
 * <h2>Concurrency</h2>
 * Per Ditto's actor-concurrency rules, no actor state is mutated from inside
 * {@link java.util.concurrent.CompletionStage} callbacks. The actor itself holds only
 * constructor-immutable fields.
 */
public final class TimeseriesIngestPublisher extends AbstractActor {

    /** Name of this actor in the actor system. */
    public static final String ACTOR_NAME = TimeseriesMessagingConstants.INGEST_PUBLISHER_ACTOR_NAME;

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Maximum attempts (incl. first) before a batch is dropped with WARN. A small ceiling keeps
     * a struggling backend from amplifying back-pressure into the publisher's mailbox; lost
     * batches surface as gaps in the timeseries data rather than silent corruption.
     */
    private static final int MAX_ATTEMPTS = 3;

    /** Kamon {@code timeseries_ingest_dropped} — incremented per batch given up after retries. */
    private static final Counter DROPPED_BATCHES =
            DittoMetrics.counter("timeseries_ingest_dropped");

    // Thread-safe variant — log calls happen inside CompletionStage callbacks (WoT resolution,
    // Patterns.ask retries) which run off the actor thread.
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(TimeseriesIngestPublisher.class);

    private final ActorRef shardRegionProxy;
    private final WotThingModelResolver wotResolver;
    private final Duration askTimeout;

    @SuppressWarnings("unused")
    private TimeseriesIngestPublisher(final ActorRef shardRegionProxy,
            final WotThingModelResolver wotResolver,
            final Duration askTimeout) {

        this.shardRegionProxy = checkNotNull(shardRegionProxy, "shardRegionProxy");
        this.wotResolver = checkNotNull(wotResolver, "wotResolver");
        this.askTimeout = checkNotNull(askTimeout, "askTimeout");
    }

    /**
     * @param shardRegionProxy proxy to the {@link TimeseriesMessagingConstants#SHARD_REGION}
     * shard region on Timeseries-service nodes.
     * @param wotResolver the shared WoT ThingModel resolver from {@code DittoWotIntegration}.
     */
    public static Props props(final ActorRef shardRegionProxy, final WotThingModelResolver wotResolver) {
        return props(shardRegionProxy, wotResolver, DEFAULT_ASK_TIMEOUT);
    }

    /** Visible for tests so the ask-timeout can be tightened to keep tests fast. */
    public static Props props(final ActorRef shardRegionProxy,
            final WotThingModelResolver wotResolver,
            final Duration askTimeout) {

        return Props.create(TimeseriesIngestPublisher.class, shardRegionProxy, wotResolver, askTimeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(IngestRequest.class, this::handleIngestRequest)
                .match(RetrySend.class, retry -> sendWithRetry(retry.command(), retry.attempt()))
                .build();
    }

    private void handleIngestRequest(final IngestRequest request) {
        final ThingEvent<?> event = request.event();
        final Thing thing = request.thing();

        final List<PropertyLeaf> leaves = ThingEventLeafExtractor.extractLeaves(event);
        if (leaves.isEmpty()) {
            return;
        }
        if (thing == null) {
            LOGGER.debug("Dropping ingest request for event <{}>: post-event Thing snapshot was null.",
                    event.getType());
            return;
        }
        final Optional<IRI> tmIri = thing.getDefinition().map(TimeseriesIngestPublisher::toTmIri);
        if (tmIri.isEmpty()) {
            LOGGER.debug("Dropping ingest request for thing <{}>: no `definition` field on the Thing.",
                    thing.getEntityId().orElse(null));
            return;
        }
        // ThingEvent's entityId is by contract a ThingId, but narrow at runtime so a stray event
        // whose entityId resolves to a different type is dropped rather than throwing.
        final Optional<ThingId> thingIdOpt = WithEntityId.getEntityIdOfType(ThingId.class, event);
        if (thingIdOpt.isEmpty()) {
            LOGGER.warn("Dropping ingest request for event <{}>: entityId <{}> is not a ThingId.",
                    event.getType(), event.getEntityId());
            return;
        }
        final ThingId thingId = thingIdOpt.get();
        final DittoHeaders headers = newDeliveryHeaders(event.getDittoHeaders());

        wotResolver.resolveThingModel(tmIri.get(), headers)
                .thenCompose(tm -> wotResolver.resolveThingModelSubmodels(tm, headers))
                .thenAccept(submodels -> {
                    final List<TimeseriesDataPoint> dataPoints =
                            buildDataPoints(thingId, leaves, thing, submodels, event);
                    if (dataPoints.isEmpty()) {
                        return;
                    }
                    sendWithRetry(IngestDataPoints.of(thingId, dataPoints, headers), 1);
                })
                .exceptionally(throwable -> {
                    final Throwable cause = throwable instanceof CompletionException
                            && throwable.getCause() != null ? throwable.getCause() : throwable;
                    LOGGER.debug("Could not resolve WoT TM for thing <{}>: {}",
                            thingId, cause.getMessage());
                    return null;
                });
    }

    private void sendWithRetry(final IngestDataPoints command, final int attempt) {
        Patterns.ask(shardRegionProxy, command, askTimeout)
                .whenComplete((response, throwable) -> {
                    if (throwable == null && response instanceof IngestDataPointsResponse) {
                        return;
                    }
                    final Throwable cause = unwrap(throwable);
                    final Object failureSubject = throwable != null
                            ? cause
                            : (response instanceof Status.Failure sf ? sf.cause() : response);
                    if (attempt < MAX_ATTEMPTS) {
                        LOGGER.debug("Retrying IngestDataPoints for thing <{}> (attempt {} of {}): {}",
                                command.getEntityId(), attempt + 1, MAX_ATTEMPTS, failureSubject);
                        // Schedule the retry on the actor thread so it doesn't race the next
                        // mailbox message. Same correlation-id signals "logical replay" to the
                        // receiving entity, which dedups via a bounded `recentlyApplied` ring.
                        getSelf().tell(new RetrySend(command, attempt + 1), getSelf());
                    } else {
                        DROPPED_BATCHES.increment();
                        LOGGER.warn("Giving up on IngestDataPoints for thing <{}> after <{}> attempts: {}",
                                command.getEntityId(), MAX_ATTEMPTS, failureSubject);
                    }
                });
    }

    @Nullable
    private static Throwable unwrap(@Nullable final Throwable throwable) {
        if (throwable instanceof CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return throwable;
    }

    private static IRI toTmIri(final ThingDefinition def) {
        return IRI.of(def.toString());
    }

    private static List<TimeseriesDataPoint> buildDataPoints(final ThingId thingId,
            final List<PropertyLeaf> leaves,
            final Thing thing,
            final Map<ThingSubmodel, ThingModel> submodels,
            final ThingEvent<?> event) {

        final Instant timestamp = event.getTimestamp().orElse(Instant.now());
        final long revision = event.getRevision();
        final List<TimeseriesDataPoint> dataPoints = new ArrayList<>();

        for (final PropertyLeaf leaf : leaves) {
            final Optional<ThingModel> submodelTm = submodels.entrySet().stream()
                    .filter(e -> leaf.featureId().equals(e.getKey().instanceName()))
                    .map(Map.Entry::getValue)
                    .findFirst();
            if (submodelTm.isEmpty()) {
                continue;
            }
            final Optional<ResolvedLeaf> resolved = WotLeafResolver.resolveLeaf(submodelTm.get(), leaf.path());
            if (resolved.isEmpty()) {
                continue;
            }
            final WotTimeseriesAnnotation annotation = resolved.get().annotation();
            final JsonPointer thingPath = JsonPointer.empty()
                    .append(JsonPointer.of("features"))
                    .append(JsonPointer.of(leaf.featureId()))
                    .append(JsonPointer.of("properties"))
                    .append(leaf.path());
            final Map<String, String> resolvedTags = resolveTags(annotation.getTags(), thing);
            dataPoints.add(TimeseriesDataPoint.of(thingId, thingPath, timestamp, leaf.value(), revision,
                    resolvedTags, resolved.get().unit()));
        }
        return dataPoints;
    }

    /** Phase 1 returns declared tags verbatim; placeholder resolution lands in Phase 2. */
    private static Map<String, String> resolveTags(final Map<String, String> declared,
            final Thing ignoredThing) {
        return declared;
    }

    private static DittoHeaders newDeliveryHeaders(final DittoHeaders source) {
        // Fresh correlation-id per delivery; reused on retry so the persistent entity recognises
        // replays via its dedup table. Inheriting other headers preserves traceparent/tracestate.
        return source.toBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * @param event the event that just persisted.
     * @param thing the entity Thing after applying the event; may be null when the persistence
     * actor is operating on a non-existent or just-deleted entity.
     */
    public record IngestRequest(ThingEvent<?> event, @Nullable Thing thing) {
        public IngestRequest {
            checkNotNull(event, "event");
        }
    }

    /** Self-message scheduling a retry on the actor thread so it doesn't race the next message. */
    private record RetrySend(IngestDataPoints command, int attempt) {}
}
