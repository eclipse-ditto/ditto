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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingDefinition;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.service.common.config.TimeseriesIngestConfig;
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
 *   between Things and Timeseries) are covered by the retry loop: up to
 *   {@link TimeseriesIngestConfig#getRetryMaxAttempts()} attempts with the same correlation-id, which the receiving
 *   entity uses to recognise replays and avoid duplicate writes (`recentlyApplied` LRU on the entity side). At most
 *   {@link TimeseriesIngestConfig#getRetryMaxPendingRetries()} batches wait for a retry at once, so a long outage at
 *   a high rate drops batches instead of filling the heap.</li>
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
 * The data points are built on the dispatcher, outside the actor, so events are processed in parallel; the only
 * state they share is the thread-safe {@link ResolvedLeafCache}. Only the finished {@link IngestDataPoints} is sent
 * back to the actor.
 */
public final class TimeseriesIngestPublisher extends AbstractActorWithTimers {

    /** Name of this actor in the actor system. */
    public static final String ACTOR_NAME = TimeseriesMessagingConstants.INGEST_PUBLISHER_ACTOR_NAME;

    private static final Duration DEFAULT_ASK_TIMEOUT = Duration.ofSeconds(5);

    private static final int RESOLVED_LEAVES_CACHE_SIZE = 10_000;

    private static final double RETRY_JITTER = 0.2;

    /**
     * Kamon {@code timeseries_ingest_resent} — incremented per data point skipped because its event wrote the same
     * value and measurement time the Thing already held.
     */
    private static final Counter RESENT = DittoMetrics.counter("timeseries_ingest_resent");

    /**
     * Kamon {@code timeseries_ingest_dropped} — incremented per batch given up after retries or failing to build, and
     * by {@link ResolvedLeafCache} per property with an invalid annotation.
     */
    private static final Counter DROPPED_BATCHES =
            DittoMetrics.counter("timeseries_ingest_dropped");
    private static final Counter DROPPED_RETRIES_EXHAUSTED =
            DittoMetrics.counter("timeseries_ingest_dropped").tag("reason", "retries_exhausted");
    private static final Counter DROPPED_RETRY_CAP =
            DittoMetrics.counter("timeseries_ingest_dropped").tag("reason", "retry_cap");

    /** Kamon {@code timeseries_ingest_pending_retries} — batches of this node waiting for a retry. */
    private static final Gauge PENDING_RETRIES = DittoMetrics.gauge("timeseries_ingest_pending_retries");

    // Thread-safe variant — log calls happen inside CompletionStage callbacks (WoT resolution,
    // data-point building, Patterns.ask retries) which run off the actor thread.
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(TimeseriesIngestPublisher.class);

    private final ActorRef shardRegionProxy;
    private final WotThingModelResolver wotResolver;
    private final Duration askTimeout;
    private final TimeseriesIngestConfig ingestConfig;
    private final ResolvedLeafCache resolvedLeaves = new ResolvedLeafCache(RESOLVED_LEAVES_CACHE_SIZE);
    // Keyed by correlation-id so a reply of a retry started before an actor restart cannot unbalance the count.
    private final Set<String> pendingRetries = new HashSet<>();

    @SuppressWarnings("unused")
    private TimeseriesIngestPublisher(final ActorRef shardRegionProxy,
            final WotThingModelResolver wotResolver,
            final TimeseriesIngestConfig ingestConfig,
            final Duration askTimeout) {

        this.shardRegionProxy = checkNotNull(shardRegionProxy, "shardRegionProxy");
        this.wotResolver = checkNotNull(wotResolver, "wotResolver");
        this.ingestConfig = checkNotNull(ingestConfig, "ingestConfig");
        this.askTimeout = checkNotNull(askTimeout, "askTimeout");
    }

    @Override
    public void postStop() throws Exception {
        PENDING_RETRIES.set(0L);
        super.postStop();
    }

    /**
     * @param shardRegionProxy proxy to the {@link TimeseriesMessagingConstants#SHARD_REGION}
     * shard region on Timeseries-service nodes.
     * @param wotResolver the shared WoT ThingModel resolver from {@code DittoWotIntegration}.
     * @param ingestConfig the configuration of the ingest.
     */
    public static Props props(final ActorRef shardRegionProxy, final WotThingModelResolver wotResolver,
            final TimeseriesIngestConfig ingestConfig) {
        return props(shardRegionProxy, wotResolver, ingestConfig, DEFAULT_ASK_TIMEOUT);
    }

    /** Visible for tests so the ask-timeout can be tightened to keep tests fast. */
    public static Props props(final ActorRef shardRegionProxy,
            final WotThingModelResolver wotResolver,
            final TimeseriesIngestConfig ingestConfig,
            final Duration askTimeout) {

        return Props.create(TimeseriesIngestPublisher.class, shardRegionProxy, wotResolver, ingestConfig,
                askTimeout);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(IngestRequest.class, this::handleIngestRequest)
                .match(IngestDataPoints.class, command -> sendWithRetry(command, 1))
                .match(RetrySend.class, this::scheduleRetry)
                .match(SendRetry.class, send -> sendWithRetry(send.retry().command(), send.retry().attempt()))
                .match(RetryEnded.class, this::endRetry)
                .build();
    }

    private void handleIngestRequest(final IngestRequest request) {
        final ThingEvent<?> event = request.event();
        final Thing thing = request.thing();
        final Thing previousThing = request.previousThing();

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

        final ActorRef self = getSelf();
        final Executor dispatcher = getContext().getDispatcher();
        final ResolvedLeafCache leafCache = resolvedLeaves;
        final Duration maxAge = ingestConfig.getMaxMeasurementAge();
        wotResolver.resolveThingModel(tmIri.get(), headers)
                .thenCompose(tm -> wotResolver.resolveThingModelSubmodels(tm, headers))
                .thenApplyAsync(submodels -> buildDataPointsOrDrop(thingId, leaves, thing, previousThing, submodels,
                        event, leafCache, maxAge), dispatcher)
                .thenAccept(dataPoints -> {
                    if (!dataPoints.isEmpty()) {
                        self.tell(IngestDataPoints.of(thingId, dataPoints, headers), self);
                    }
                })
                .exceptionally(throwable -> {
                    final Throwable cause = throwable instanceof CompletionException
                            && throwable.getCause() != null ? throwable.getCause() : throwable;
                    LOGGER.debug("Could not resolve the WoT TM or build data points for thing <{}>: {}",
                            thingId, cause.getMessage());
                    return null;
                });
    }

    private void sendWithRetry(final IngestDataPoints command, final int attempt) {
        final ActorRef self = getSelf();
        final int maxAttempts = ingestConfig.getRetryMaxAttempts();
        Patterns.ask(shardRegionProxy, command, askTimeout)
                .whenComplete((response, throwable) -> {
                    if (throwable == null && response instanceof IngestDataPointsResponse) {
                        if (attempt > 1) {
                            self.tell(new RetryEnded(retryKey(command)), self);
                        }
                        return;
                    }
                    final Object failure = throwable != null
                            ? unwrap(throwable)
                            : (response instanceof Status.Failure sf ? sf.cause() : response);
                    if (attempt < maxAttempts) {
                        LOGGER.debug("Retrying IngestDataPoints for thing <{}> (attempt {} of {}): {}",
                                command.getEntityId(), attempt + 1, maxAttempts, describe(failure));
                        // Schedule the retry on the actor thread so it doesn't race the next
                        // mailbox message. Same correlation-id signals "logical replay" to the
                        // receiving entity, which dedups via a bounded `recentlyApplied` ring.
                        self.tell(new RetrySend(command, attempt + 1), self);
                    } else {
                        DROPPED_BATCHES.increment();
                        DROPPED_RETRIES_EXHAUSTED.increment();
                        LOGGER.warn("Giving up on IngestDataPoints for thing <{}> after <{}> attempts: {}",
                                command.getEntityId(), maxAttempts, describe(failure));
                        if (attempt > 1) {
                            self.tell(new RetryEnded(retryKey(command)), self);
                        }
                    }
                });
    }

    private void scheduleRetry(final RetrySend retry) {
        final String key = retryKey(retry.command());
        if (!pendingRetries.contains(key)) {
            if (pendingRetries.size() >= ingestConfig.getRetryMaxPendingRetries()) {
                DROPPED_BATCHES.increment();
                DROPPED_RETRY_CAP.increment();
                LOGGER.debug("Dropping IngestDataPoints for thing <{}>: <{}> batches already wait for a retry.",
                        retry.command().getEntityId(), pendingRetries.size());
                return;
            }
            pendingRetries.add(key);
            PENDING_RETRIES.set((long) pendingRetries.size());
        }
        getTimers().startSingleTimer(key, new SendRetry(retry), retryDelay(ingestConfig, retry.attempt()));
    }

    private void endRetry(final RetryEnded ended) {
        pendingRetries.remove(ended.key());
        PENDING_RETRIES.set((long) pendingRetries.size());
    }

    static Duration retryDelay(final TimeseriesIngestConfig config, final int attempt) {
        final Duration delay =
                config.getRetryBackoff().multipliedBy(1L << Math.min(Math.max(attempt - 2, 0), 30));
        final Duration capped = delay.compareTo(config.getRetryMaxBackoff()) > 0 ? config.getRetryMaxBackoff() : delay;
        final double jitter = 1 + ThreadLocalRandom.current().nextDouble(-RETRY_JITTER, RETRY_JITTER);
        return Duration.ofMillis(Math.round(capped.toMillis() * jitter));
    }

    private static String retryKey(final IngestDataPoints command) {
        return command.getDittoHeaders().getCorrelationId()
                .orElseGet(() -> command.getEntityId() + "@" + System.identityHashCode(command));
    }

    private static String describe(@Nullable final Object failure) {
        if (failure instanceof DittoRuntimeException dre) {
            return dre.getErrorCode() + ": " + dre.getMessage();
        }
        if (failure instanceof Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
        return failure == null ? "null" : failure.getClass().getSimpleName();
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

    private static List<TimeseriesDataPoint> buildDataPointsOrDrop(final ThingId thingId,
            final List<PropertyLeaf> leaves,
            final Thing thing,
            @Nullable final Thing previousThing,
            final Map<ThingSubmodel, ThingModel> submodels,
            final ThingEvent<?> event,
            final ResolvedLeafCache leafCache,
            final Duration maxMeasurementAge) {

        try {
            return buildDataPoints(thingId, leaves, thing, previousThing, submodels, event, leafCache,
                    maxMeasurementAge);
        } catch (final RuntimeException e) {
            DROPPED_BATCHES.increment();
            LOGGER.warn("Dropping timeseries data points of thing <{}> for event <{}>: {}",
                    thingId, event.getType(), e.toString());
            return List.of();
        }
    }

    private static List<TimeseriesDataPoint> buildDataPoints(final ThingId thingId,
            final List<PropertyLeaf> leaves,
            final Thing thing,
            @Nullable final Thing previousThing,
            final Map<ThingSubmodel, ThingModel> submodels,
            final ThingEvent<?> event,
            final ResolvedLeafCache leafCache,
            final Duration maxMeasurementAge) {

        final Instant eventTime = event.getTimestamp().orElse(Instant.now());
        final MeasurementTimes times = new MeasurementTimes(thingId, event, eventTime,
                eventTime.minus(maxMeasurementAge), previousThing);
        final long revision = event.getRevision();
        final List<TimeseriesDataPoint> dataPoints = new ArrayList<>();

        // Resolve tag placeholders (e.g. "{{ thing-json:attributes/building }}") against the
        // post-event Thing once per event — all leaves of one event share the same Thing snapshot.
        final Supplier<JsonObject> thingJson = memoize(() -> thing.toJson(FieldType.REGULAR));
        final Supplier<TagExpressionResolver> tagResolver =
                memoize(() -> new TagExpressionResolver(thingJson.get()));

        for (final PropertyLeaf leaf : leaves) {
            final Optional<Map.Entry<ThingSubmodel, ThingModel>> submodel = submodels.entrySet().stream()
                    .filter(e -> leaf.featureId().equals(e.getKey().instanceName()))
                    .findFirst();
            if (submodel.isEmpty()) {
                continue;
            }
            final Optional<ResolvedLeaf> resolved =
                    leafCache.resolve(submodel.get().getKey(), submodel.get().getValue(), leaf.path());
            if (resolved.isEmpty()) {
                continue;
            }
            final WotTimeseriesAnnotation annotation = resolved.get().annotation();
            final JsonPointer featureProperties = JsonPointer.empty()
                    .append(JsonPointer.of("features"))
                    .append(JsonPointer.of(leaf.featureId()))
                    .append(JsonPointer.of("properties"));
            final JsonPointer thingPath = featureProperties.append(leaf.path());
            final Optional<Instant> timestamp = annotation.getTimePath()
                    .map(timePath -> times.of(featureProperties.append(timePath), thingPath, leaf.value(), thingJson))
                    .orElse(Optional.of(eventTime));
            if (timestamp.isEmpty()) {
                continue;
            }
            final Map<String, JsonValue> resolvedTags =
                    resolveTags(annotation.getTags(), tagResolver, thing);
            dataPoints.add(TimeseriesDataPoint.of(thingId, thingPath, timestamp.get(), leaf.value(), revision,
                    resolvedTags, resolved.get().unit()));
        }
        return dataPoints;
    }

    /**
     * Decides the time of the data points of one event whose annotation declares a {@code time} property. The
     * property counts only if this event wrote it: a value updated without its time would otherwise carry the time
     * of an earlier measurement. Falls back to the event time when the event did not write the property, it is
     * missing or no ISO-8601 instant, or it lies after the event (device clocks run ahead) or further back than the
     * maximum measurement age (e.g. a reset device clock). A value and time the Thing already held, re-sent by a
     * device or rewritten by a replace, give no data point at all.
     */
    private static final class MeasurementTimes {

        private final ThingId thingId;
        private final ThingEvent<?> event;
        private final Instant eventTime;
        private final Instant earliest;
        private final Supplier<Optional<JsonObject>> previousThingJson;

        private MeasurementTimes(final ThingId thingId, final ThingEvent<?> event, final Instant eventTime,
                final Instant earliest, @Nullable final Thing previousThing) {

            this.thingId = thingId;
            this.event = event;
            this.eventTime = eventTime;
            this.earliest = earliest;
            previousThingJson = memoize(() -> Optional.ofNullable(previousThing)
                    .map(previous -> previous.toJson(FieldType.REGULAR)));
        }

        /**
         * @return the time of the data point of {@code value} at {@code valuePath}, or empty to skip the re-sent
         * point.
         */
        Optional<Instant> of(final JsonPointer timePath, final JsonPointer valuePath, final JsonValue value,
                final Supplier<JsonObject> thingJson) {

            final Optional<JsonValue> written = valueWrittenBy(event, timePath);
            if (written.isEmpty()) {
                final boolean missing =
                        thingJson.get().getValue(timePath).filter(time -> !time.isNull()).isEmpty();
                return Optional.of(fallBack(missing ? TimeFallback.MISSING : TimeFallback.UNCHANGED, timePath));
            }
            if (isResent(timePath, written.get(), valuePath, value)) {
                RESENT.increment();
                LOGGER.debug("Skipping the data point <{}> of thing <{}>: value and time <{}> are unchanged.",
                        valuePath, thingId, written.get());
                return Optional.empty();
            }
            final Instant measured;
            try {
                measured = Instant.parse(written.get().asString());
            } catch (final UnsupportedOperationException | DateTimeParseException e) {
                return Optional.of(fallBack(TimeFallback.INVALID, timePath));
            }
            if (measured.isAfter(eventTime)) {
                return Optional.of(fallBack(TimeFallback.FUTURE, timePath));
            }
            if (measured.isBefore(earliest)) {
                return Optional.of(fallBack(TimeFallback.PAST, timePath));
            }
            return Optional.of(measured);
        }

        private boolean isResent(final JsonPointer timePath, final JsonValue time, final JsonPointer valuePath,
                final JsonValue value) {

            return previousThingJson.get()
                    .filter(previous -> previous.getValue(timePath).filter(time::equals).isPresent())
                    .filter(previous -> previous.getValue(valuePath).filter(value::equals).isPresent())
                    .isPresent();
        }

        private Instant fallBack(final TimeFallback reason, final JsonPointer timePath) {
            reason.counter.increment();
            LOGGER.debug("Using the event time for a data point of thing <{}>: time property <{}> is {}.",
                    thingId, timePath, reason.tag);
            return eventTime;
        }

        private static Optional<JsonValue> valueWrittenBy(final ThingEvent<?> event, final JsonPointer path) {
            final JsonPointer resourcePath = event.getResourcePath();
            if (resourcePath.getLevelCount() > path.getLevelCount()) {
                return Optional.empty();
            }
            for (int level = 0; level < resourcePath.getLevelCount(); level++) {
                if (!resourcePath.get(level).equals(path.get(level))) {
                    return Optional.empty();
                }
            }
            final JsonPointer relative =
                    path.getSubPointer(resourcePath.getLevelCount()).orElse(JsonPointer.empty());
            return event.getEntity()
                    .flatMap(entity -> relative.isEmpty() ? Optional.of(entity)
                            : entity.isObject() ? entity.asObject().getValue(relative) : Optional.empty())
                    .filter(written -> !written.isNull());
        }
    }

    /**
     * Why a data point declaring a time field is stamped with the event time, counted in Kamon
     * {@code timeseries_ingest_time_fallback} with the {@code reason} tag.
     */
    private enum TimeFallback {
        UNCHANGED("unchanged"),
        MISSING("missing"),
        INVALID("invalid"),
        FUTURE("future"),
        PAST("past");

        private final String tag;
        private final Counter counter;

        TimeFallback(final String tag) {
            this.tag = tag;
            counter = DittoMetrics.counter("timeseries_ingest_time_fallback").tag("reason", tag);
        }
    }

    /**
     * Resolves each declared tag value against the post-event Thing, replacing WoT placeholder
     * expressions (e.g. {@code "{{ thing-json:attributes/building }}"}) with the Thing's current
     * values; constant tag values pass through unchanged. See {@link TagExpressionResolver} for the type of a
     * resolved value.
     * <p>
     * A tag whose placeholder resolves to no value on this Thing — or whose expression is malformed —
     * is <em>dropped</em> for this data point rather than stored empty or failing ingestion. Tags are
     * a point-in-time snapshot, so each point keeps exactly the values that were resolvable when it
     * was recorded.
     */
    private static Map<String, JsonValue> resolveTags(final Map<String, String> declared,
            final Supplier<TagExpressionResolver> resolver, final Thing thing) {

        final Map<String, JsonValue> resolved = new LinkedHashMap<>(declared.size());
        for (final Map.Entry<String, String> entry : declared.entrySet()) {
            final String expression = entry.getValue();
            if (!expression.contains("{{")) {
                resolved.put(entry.getKey(), JsonValue.of(expression));
                continue;
            }
            try {
                resolver.get().resolve(expression).ifPresent(value -> resolved.put(entry.getKey(), value));
            } catch (final RuntimeException e) {
                // Malformed/unknown placeholder in the model — drop this tag, never break ingest.
                LOGGER.debug("Dropping timeseries tag <{}> for thing <{}>: {}",
                        entry.getKey(), thing.getEntityId().orElse(null), e.getMessage());
            }
        }
        return resolved;
    }

    private static <T> Supplier<T> memoize(final Supplier<T> supplier) {
        return new Supplier<T>() {
            @Nullable private T value;

            @Override
            public T get() {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        };
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
     * @param previousThing the entity Thing before the event; null when the event created it.
     */
    public record IngestRequest(ThingEvent<?> event, @Nullable Thing thing, @Nullable Thing previousThing) {
        public IngestRequest {
            checkNotNull(event, "event");
        }

        public IngestRequest(final ThingEvent<?> event, @Nullable final Thing thing) {
            this(event, thing, null);
        }
    }

    /** Self-message scheduling a retry on the actor thread so it doesn't race the next message. */
    private record RetrySend(IngestDataPoints command, int attempt) {}

    private record SendRetry(RetrySend retry) {}

    private record RetryEnded(String key) {}
}
