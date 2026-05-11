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
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertyModified;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPoints;
import org.eclipse.ditto.timeseries.api.commands.IngestDataPointsResponse;
import org.eclipse.ditto.timeseries.model.Ingest;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.WotTimeseriesAnnotation;
import org.eclipse.ditto.wot.api.resolver.ThingSubmodel;
import org.eclipse.ditto.wot.api.resolver.WotThingModelResolver;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.ThingModel;

/**
 * Per-Things-service-node actor that turns selected {@link ThingEvent}s into
 * {@link IngestDataPoints} batches and asks the Timeseries shard region with bounded
 * retries until the persistent entity acks.
 * <p>
 * Phase 1 scope: only {@link FeaturePropertyCreated} and {@link FeaturePropertyModified}.
 * Other event shapes (full-Thing modify, full-feature replace, multi-property updates,
 * deletes) need the publisher to enumerate every changed leaf path against the WoT TM,
 * intentionally deferred to Phase 2 alongside placeholder-resolved tags. Events outside
 * this scope are silently dropped — this actor is opt-in via the {@code ditto:timeseries}
 * annotation, and a Thing without the annotation receives no traffic regardless.
 *
 * <h2>Delivery model</h2>
 * The publisher itself is non-persistent: it does not survive a Things-service node crash
 * between {@code ThingPersistenceActor} delivering an {@link IngestRequest} and the ask
 * round-trip. Stronger durability lives on the receiving side — the Timeseries shard
 * region's entity is a Pekko-persistent actor that journals each batch before writing to
 * MongoDB Time Series. So once the publisher's ask has reached the shard region (which
 * cluster sharding buffers across rebalances and short network blips), durability is
 * the entity's responsibility.
 * <p>
 * The publisher retries on missing acks up to {@link #MAX_ATTEMPTS} times with the same
 * correlation-id. The entity uses that correlation-id to recognise replays and skip
 * duplicate writes (idempotent ingestion).
 *
 * <h2>WoT resolution</h2>
 * The {@link WotThingModelResolver} caches resolved ThingModels and submodel maps
 * internally, so steady-state property updates take a hot-path lookup; cold lookups
 * (first event for a Thing, or after the cache evicts) pay one HTTP fetch per submodel.
 * We never block on the resolution stages — they are composed with {@code thenCompose}
 * on the WoT executor; the actor thread proceeds to the next message immediately.
 *
 * <h2>Concurrency</h2>
 * Per Ditto's actor-concurrency rules, no actor state is mutated from inside
 * {@link java.util.concurrent.CompletionStage} callbacks. The actor itself holds only
 * constructor-immutable fields.
 */
public final class TimeseriesIngestPublisher extends AbstractActor {

    /**
     * Name of this actor in the actor system. Mirrors
     * {@link TimeseriesMessagingConstants#INGEST_PUBLISHER_ACTOR_NAME}.
     */
    public static final String ACTOR_NAME = TimeseriesMessagingConstants.INGEST_PUBLISHER_ACTOR_NAME;

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Maximum number of attempts (including the first) before a batch is dropped with
     * WARN. A small ceiling keeps a struggling backend from amplifying back-pressure
     * into the publisher's mailbox; lost batches surface in the log and would manifest
     * as gaps in the timeseries data rather than silent corruption.
     */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Counter incremented every time the publisher exhausts {@link #MAX_ATTEMPTS} retries
     * for a batch and gives up. Visible in the Kamon dashboard as
     * {@code timeseries_ingest_dropped} so a struggling timeseries backend produces an
     * observable signal beyond a single WARN log line.
     */
    private static final Counter DROPPED_BATCHES =
            DittoMetrics.counter("timeseries_ingest_dropped");

    // Thread-safe variant — log calls happen inside CompletionStage callbacks (WoT
    // resolution, Patterns.ask retries) which run off the actor thread. The diagnostic
    // adapter is single-threaded by design.
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
     * Returns Pekko {@code Props} with the default ask-timeout.
     *
     * @param shardRegionProxy proxy to the {@link TimeseriesMessagingConstants#SHARD_REGION}
     * shard region on Timeseries-service nodes.
     * @param wotResolver the shared WoT ThingModel resolver from {@code DittoWotIntegration}.
     * @return the props.
     */
    public static Props props(final ActorRef shardRegionProxy, final WotThingModelResolver wotResolver) {
        return props(shardRegionProxy, wotResolver, DEFAULT_ASK_TIMEOUT);
    }

    /**
     * Visible for tests so the ask-timeout can be tightened well below default to keep
     * tests fast.
     */
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

        if (!(event instanceof FeaturePropertyCreated) && !(event instanceof FeaturePropertyModified)) {
            return;
        }
        if (thing == null) {
            return;
        }
        final Optional<IRI> tmIri = thing.getDefinition().map(TimeseriesIngestPublisher::toTmIri);
        if (tmIri.isEmpty()) {
            return;
        }

        // ThingEvent's entityId is by contract a ThingId, but we let WithEntityId narrow the
        // type at runtime so a stray event whose entityId resolves to a different type is
        // logged and dropped rather than crashing this actor with ClassCastException.
        final Optional<ThingId> thingIdOpt = WithEntityId.getEntityIdOfType(ThingId.class, event);
        if (thingIdOpt.isEmpty()) {
            LOGGER.warn("Dropping ingest request for event <{}>: entityId <{}> is not a ThingId.",
                    event.getType(), event.getEntityId());
            return;
        }
        final ThingId thingId = thingIdOpt.get();
        final FeaturePropertyEvent fpe = FeaturePropertyEvent.from(event);

        final JsonPointer thingPath = JsonPointer.empty()
                .append(JsonPointer.of("features"))
                .append(JsonPointer.of(fpe.featureId()))
                .append(JsonPointer.of("properties"))
                .append(fpe.propertyPath());

        final DittoHeaders headers = newDeliveryHeaders(event.getDittoHeaders());

        wotResolver.resolveThingModel(tmIri.get(), headers)
                .thenCompose(tm -> wotResolver.resolveThingModelSubmodels(tm, headers))
                .thenAccept(submodels -> {
                    final Optional<TimeseriesDataPoint> dp =
                            buildDataPoint(thingId, fpe, thing, thingPath, submodels, event);
                    if (dp.isEmpty()) {
                        return;
                    }
                    final IngestDataPoints command =
                            IngestDataPoints.of(thingId, List.of(dp.get()), headers);
                    sendWithRetry(command, 1);
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
                        // Schedule the retry on the actor thread so it doesn't race the
                        // next mailbox message. Same correlation-id signals "logical
                        // replay" to the persistent entity, which dedups against its
                        // last-applied table.
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

    private static Optional<TimeseriesDataPoint> buildDataPoint(final ThingId thingId,
            final FeaturePropertyEvent fpe,
            final Thing thing,
            final JsonPointer thingPath,
            final Map<ThingSubmodel, ThingModel> submodels,
            final ThingEvent<?> event) {

        final Optional<ThingModel> submodelTm = submodels.entrySet().stream()
                .filter(e -> fpe.featureId().equals(e.getKey().instanceName()))
                .map(Map.Entry::getValue)
                .findFirst();
        if (submodelTm.isEmpty()) {
            return Optional.empty();
        }

        final String propertyName = fpe.propertyPath().getRoot()
                .map(Object::toString)
                .orElse(null);
        if (propertyName == null) {
            return Optional.empty();
        }

        final Property property = submodelTm.get().getProperties()
                .map(props -> props.get(propertyName))
                .orElse(null);
        if (property == null) {
            return Optional.empty();
        }

        final Optional<WotTimeseriesAnnotation> annotation =
                WotTimeseriesAnnotation.findInProperty(property.toJson());
        if (annotation.isEmpty() || annotation.get().getIngest() != Ingest.ALL) {
            return Optional.empty();
        }

        final JsonValue value = fpe.value();
        if (!value.isNull() && (value.isObject() || value.isArray())) {
            return Optional.empty();
        }

        final Instant timestamp = event.getTimestamp().orElse(Instant.now());
        final long revision = event.getRevision();
        final Map<String, String> resolvedTags = resolveTags(annotation.get().getTags(), thing);
        final String unit = property.getUnit().orElse(null);

        return Optional.of(TimeseriesDataPoint.of(thingId, thingPath, timestamp, value, revision,
                resolvedTags, unit));
    }

    /**
     * Phase 1 returns the declared tags verbatim. Placeholder resolution lands in
     * Phase 2 with the existing Ditto placeholder pipeline.
     */
    private static Map<String, String> resolveTags(final Map<String, String> declared,
            final Thing ignoredThing) {
        return declared;
    }

    private static DittoHeaders newDeliveryHeaders(final DittoHeaders source) {
        // Mint a fresh correlation-id per delivery; on retry we keep this id so the
        // persistent entity can recognise replays via its dedup table. Inheriting other
        // headers from the originating event preserves traceparent/tracestate for
        // distributed tracing.
        return source.toBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Internal request to the publisher. Sent fire-and-forget by
     * {@code ThingPersistenceActor#publishEvent}.
     *
     * @param event the event that just persisted.
     * @param thing the entity Thing after applying the event; may be null when the
     * persistence actor is operating on a non-existent or just-deleted entity.
     */
    public record IngestRequest(ThingEvent<?> event, @Nullable Thing thing) {
        public IngestRequest {
            checkNotNull(event, "event");
        }
    }

    /**
     * Self-message used to schedule a retry on the actor thread so the retry doesn't
     * race the next mailbox message.
     */
    private record RetrySend(IngestDataPoints command, int attempt) {}

    /**
     * Narrow projection over the two property-event types we handle in Phase 1, so the
     * rest of {@link #handleIngestRequest} doesn't need to instanceof-check repeatedly.
     */
    private record FeaturePropertyEvent(String featureId, JsonPointer propertyPath, JsonValue value) {

        static FeaturePropertyEvent from(final ThingEvent<?> event) {
            if (event instanceof FeaturePropertyCreated created) {
                return new FeaturePropertyEvent(created.getFeatureId(),
                        created.getPropertyPointer(), created.getPropertyValue());
            }
            final FeaturePropertyModified modified = (FeaturePropertyModified) event;
            return new FeaturePropertyEvent(modified.getFeatureId(),
                    modified.getPropertyPointer(), modified.getPropertyValue());
        }
    }
}
