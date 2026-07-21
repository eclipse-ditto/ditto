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
package org.eclipse.ditto.timeseries.mongodb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.SortOrder;
import org.eclipse.ditto.timeseries.model.TimeseriesCursor;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Default Ditto-shipped {@link TimeseriesAdapter} backed by MongoDB Time Series collections.
 * <p>
 * Phase 1 implements the full lifecycle ({@link #initialize}, {@link #shutdown},
 * {@link #getHealth}), the ingestion paths ({@link #write}, {@link #writeBatch}) and the raw
 * read path ({@link #query}). Per-namespace collections are created on first use as native
 * MongoDB Time Series collections (i.e. {@code createCollection} with {@code TimeSeriesOptions})
 * — once a collection exists with that storage layout MongoDB applies bucketed columnar storage
 * which is what we actually want for timeseries reads. The granularity is configured globally
 * (see {@link MongoDbTimeseriesAdapterConfig#getGranularity}); a future phase will pick it
 * per-(namespace, cadence) once we have ingestion-side cadence detection.
 */
@ThreadSafe
public final class MongoDbTimeseriesAdapter implements TimeseriesAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDbTimeseriesAdapter.class);

    /**
     * MongoDB field name for the time-series timestamp axis. Stored as the {@code timeField} in the
     * collection's TimeSeriesOptions so MongoDB knows which field to bucket on. Mirrors the value
     * already in use by {@link TimeseriesBsonMapper#FIELD_TIMESTAMP}.
     */
    static final String TIME_FIELD = TimeseriesBsonMapper.FIELD_TIMESTAMP;

    /**
     * MongoDB field name for the meta document. Stored as the {@code metaField} in the
     * TimeSeriesOptions so MongoDB co-locates points with the same {@code thingId}/{@code path}
     * tuple in the same bucket — which is what makes per-thing reads fast.
     */
    static final String META_FIELD = "meta";

    private final AtomicReference<State> state = new AtomicReference<>(State.notInitialized());

    /**
     * Per-collection-name "ensured" cache. The first writer for a given namespace triggers an
     * idempotent {@code createCollection} with the appropriate {@link TimeSeriesOptions}; concurrent
     * writers attach to the same in-flight {@link CompletionStage}. Failed creates are evicted so
     * the next caller can retry.
     */
    private final ConcurrentHashMap<String, CompletionStage<Void>> ensuredCollections =
            new ConcurrentHashMap<>();

    @Override
    public Capabilities capabilities() {
        // Config-driven: the capabilities block declares what this deployment's MongoDB supports
        // (native query, downsampling, pushed-down aggregations, native fill/retention). Defaults
        // (DEFAULT_CAPABILITIES) match a modern MongoDB 5.0+. Falls back to the default before the
        // adapter is initialised.
        final MongoDbTimeseriesAdapterConfig config = state.get().config;
        return config != null
                ? config.getCapabilities()
                : DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> initialize(final TimeseriesAdapterConfig config) {
        checkNotNull(config, "config");
        if (!(config instanceof MongoDbTimeseriesAdapterConfig)) {
            return failedStage(new IllegalArgumentException(
                    "MongoDbTimeseriesAdapter requires a MongoDbTimeseriesAdapterConfig but got: " +
                            config.getClass().getName()));
        }
        final MongoDbTimeseriesAdapterConfig mongoConfig = (MongoDbTimeseriesAdapterConfig) config;

        // Reuse Ditto's shared connection-setup path: MongoClientWrapper handles AWS IAM
        // (assumeRoleWithWebIdentity via AwsAuthenticationHelper, credential refresh),
        // pool sizing, SSL and circuit-breaker tuning — same code the sibling services'
        // pekko-persistence-mongodb plugin uses. The default database segment comes from
        // `ditto.mongodb.database` and is exposed via getDefaultDatabase().
        final DittoMongoClient client;
        final MongoDatabase database;
        try {
            client = MongoClientWrapper.newInstance(mongoConfig.getMongoDbConfig());
            database = client.getDefaultDatabase();
        } catch (final RuntimeException e) {
            return failedStage(e);
        }
        // Round-trip a {ping:1} command to actually prove the cluster is reachable. MongoClients
        // construction is lazy: without this probe an unreachable backend would still let
        // initialize() complete and getHealth() return UP, so a k8s /alive probe would stay green
        // for a service that cannot read or write. Failing the stage here makes the lying-up
        // failure mode observable at start-up time and feeds the adapter-init-failure log in
        // TimeseriesRootActor.
        return asVoidStage(database.runCommand(new Document("ping", 1)))
                .whenComplete((ignored, throwable) -> {
                    if (throwable == null) {
                        state.set(State.initialized(mongoConfig, client, database));
                    } else {
                        try {
                            client.close();
                        } catch (final RuntimeException ignoredClose) {
                            // Best-effort close — the ping failure is the surfaceable error.
                        }
                    }
                });
    }

    @Override
    public CompletionStage<Void> shutdown() {
        final State previous = state.getAndSet(State.shutDown());
        if (previous.client != null) {
            try {
                previous.client.close();
            } catch (final RuntimeException e) {
                return failedStage(e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public HealthStatus getHealth() {
        return state.get().health;
    }

    @Override
    public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
        checkNotNull(dataPoint, "dataPoint");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return failedStage(new IllegalStateException(
                    "MongoDbTimeseriesAdapter is not initialised."));
        }

        final Document document;
        try {
            document = TimeseriesBsonMapper.toDocument(dataPoint);
        } catch (final RuntimeException e) {
            return failedStage(e);
        }

        return ensureCollection(current, dataPoint.getThingId())
                .thenCompose(collection -> asVoidStage(collection.insertOne(document)));
    }

    @Override
    public CompletionStage<Void> writeBatch(final List<TimeseriesDataPoint> dataPoints) {
        checkNotNull(dataPoints, "dataPoints");
        if (dataPoints.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return failedStage(new IllegalStateException(
                    "MongoDbTimeseriesAdapter is not initialised."));
        }

        // Group by collection (i.e. by Thing namespace) and issue one insertMany per collection.
        // For the MVP we expect callers to batch by Thing already; the loop preserves correctness
        // for mixed-namespace batches by walking once and grouping inline.
        final java.util.LinkedHashMap<String, List<Document>> grouped = new java.util.LinkedHashMap<>();
        final java.util.LinkedHashMap<String, ThingId> firstThingIdByCollection = new java.util.LinkedHashMap<>();
        for (final TimeseriesDataPoint dp : dataPoints) {
            checkNotNull(dp, "dataPoint in batch");
            final String name = collectionNameFor(current.config, dp.getThingId());
            final Document document;
            try {
                document = TimeseriesBsonMapper.toDocument(dp);
            } catch (final RuntimeException e) {
                return failedStage(e);
            }
            grouped.computeIfAbsent(name, k -> new ArrayList<>()).add(document);
            firstThingIdByCollection.putIfAbsent(name, dp.getThingId());
        }

        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final java.util.Map.Entry<String, List<Document>> entry : grouped.entrySet()) {
            final ThingId firstThingId = firstThingIdByCollection.get(entry.getKey());
            final List<Document> docs = entry.getValue();
            chain = chain.thenCompose(ignored -> ensureCollection(current, firstThingId)
                    .thenCompose(coll -> asVoidStage(coll.insertMany(docs))));
        }
        return chain;
    }

    @Override
    public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
        checkNotNull(query, "query");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return failedStage(new IllegalStateException(
                    "MongoDbTimeseriesAdapter is not initialised."));
        }

        final List<JsonPointer> paths = query.getPaths();
        if (paths.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Issue one query per requested path and collect the per-path results in request order.
        // queryOnePath picks the read strategy from the query: a raw find() when no step/aggregation
        // is set, otherwise the $dateTrunc/$group downsampling pipeline or a window-function path
        // (derivative/rate/integral/percentile). The fan-out runs the paths concurrently; each path's
        // scan is bounded independently by the adapter's per-path ceiling (max-query-result-size).
        final List<CompletionStage<TimeseriesQueryResult>> perPath = new ArrayList<>(paths.size());
        for (final JsonPointer path : paths) {
            perPath.add(queryOnePath(current, query, path));
        }
        return collectInOrder(perPath);
    }

    @Override
    public CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId,
            final JsonPointer path, final Instant from, final Instant to, final int limit) {

        checkNotNull(thingId, "thingId");
        checkNotNull(path, "path");
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return failedStage(new IllegalStateException(
                    "MongoDbTimeseriesAdapter is not initialised."));
        }

        // Same heap-guard semantics as the raw read path: cap at the smaller of the caller's limit
        // and the configured ceiling; a non-positive limit means "the ceiling". getCollection (not
        // ensureCollection) so a scan never provisions a collection — a find on a missing collection
        // yields an empty cursor, which is the desired "no data yet".
        final int maxPoints = current.config.getMaxQueryResultSize();
        final int effectiveLimit = (limit <= 0) ? maxPoints : Math.min(limit, maxPoints);
        final Bson filter = rangeFilter(thingId, path, from, to);
        final FindPublisher<Document> find = getCollection(current, thingId).find(filter)
                .sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP))
                .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .limit(effectiveLimit);
        return collectAll(find).thenApply(docs -> {
            final List<TimeseriesDataValue> out = new ArrayList<>(docs.size());
            for (final Document doc : docs) {
                out.add(TimeseriesBsonMapper.toDataValue(doc));
            }
            return Collections.unmodifiableList(out);
        });
    }

    private static Bson pathFilter(final ThingId thingId, final JsonPointer path,
            final TimeseriesQuery query) {
        return rangeFilter(thingId, path, query.getFrom(), query.getTo());
    }

    private static Bson rangeFilter(final ThingId thingId, final JsonPointer path,
            final Instant from, final Instant to) {
        return Filters.and(
                Filters.eq("meta." + TimeseriesBsonMapper.META_THING_ID, thingId.toString()),
                Filters.eq("meta." + TimeseriesBsonMapper.META_PATH, path.toString()),
                Filters.gte(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(from)),
                Filters.lt(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(to)));
    }

    private CompletionStage<TimeseriesQueryResult> queryOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final long startNanos = System.nanoTime();
        return runQueryStrategy(current, query, path)
                .whenComplete((result, error) -> logQueryCost(query, path, result, error, startNanos));
    }

    /**
     * Picks and runs the read strategy for a single path: a raw {@code find()} when no
     * step/aggregation is set, the {@code $dateTrunc}/{@code $group} downsampling pipeline for a
     * bucketed aggregation, or a point-derived window aggregation
     * ({@code derivative}/{@code rate}/{@code integral}/{@code percentile}).
     */
    private CompletionStage<TimeseriesQueryResult> runQueryStrategy(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final Optional<Duration> stepOpt = query.getStep();
        final Optional<Aggregation> aggOpt = query.getAggregation();
        if (aggOpt.isPresent()) {
            final Aggregation agg = aggOpt.get();
            // group-aggregation-requires-step is enforced at the model layer (TimeseriesQuery.of);
            // by the time a command reaches here the invariant holds, so stepOpt is present.
            if (agg.requiresStep() && stepOpt.isPresent()) {
                return aggregateOnePath(current, query, path, stepOpt.get(), agg);
            }
            // derivative / rate / integral / percentile — computed from fetched points.
            return aggregateAdvancedOnePath(current, query, path, agg);
        }
        return queryOnePathRaw(current, query, path);
    }

    /**
     * Emits a per-path query-cost line at DEBUG: the read strategy, query shape (range, step,
     * aggregation, fill), the result size and the wall-clock latency. This is the evidence base for
     * spotting expensive reads (broad ranges, tiny steps, heavy window aggregations) without
     * guessing, and it feeds the push-down/planner decisions of later phases. It is gated on DEBUG
     * so it adds no overhead in production unless explicitly enabled; the scan <em>volume</em> of
     * the memory-bound window aggregations is logged separately in {@link #fetchRawTimedValues}.
     */
    private static void logQueryCost(final TimeseriesQuery query, final JsonPointer path,
            @Nullable final TimeseriesQueryResult result, @Nullable final Throwable error,
            final long startNanos) {

        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        final long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        final long rangeSeconds = Duration.between(query.getFrom(), query.getTo()).getSeconds();
        final String strategy = query.getAggregation()
                .map(agg -> agg.requiresStep() && query.getStep().isPresent()
                        ? "group-agg" : "advanced-agg")
                .orElse("raw");
        final String agg = query.getAggregation().map(Aggregation::getName).orElse("none");
        final String step = query.getStep().map(Duration::toString).orElse("none");
        final String fill = query.getFillStrategy().map(FillStrategy::getName).orElse("none");
        if (error != null) {
            LOGGER.debug("Timeseries query cost: thing=<{}> path=<{}> strategy=<{}> agg=<{}> " +
                            "step=<{}> fill=<{}> rangeSeconds=<{}> elapsedMs=<{}> outcome=<error:{}>",
                    query.getThingId(), path, strategy, agg, step, fill, rangeSeconds, elapsedMs,
                    error.getClass().getSimpleName());
        } else {
            final long resultPoints = result != null ? result.getMeta().getCount() : 0L;
            LOGGER.debug("Timeseries query cost: thing=<{}> path=<{}> strategy=<{}> agg=<{}> " +
                            "step=<{}> fill=<{}> rangeSeconds=<{}> resultPoints=<{}> elapsedMs=<{}> " +
                            "outcome=<ok>",
                    query.getThingId(), path, strategy, agg, step, fill, rangeSeconds, resultPoints,
                    elapsedMs);
        }
    }

    private CompletionStage<TimeseriesQueryResult> queryOnePathRaw(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        final boolean descending = query.getOrder().orElse(SortOrder.ASC) == SortOrder.DESC;

        // Cursor pagination is keyset-based: when a cursor is present, restrict to points that sort
        // strictly after it under the query's order. The model layer has already validated that a
        // cursor only reaches here for a single-path raw read, and rejects a malformed one — so
        // decode cannot fail at this point.
        final Optional<TimeseriesCursor> cursorOpt = query.getCursor().map(TimeseriesCursor::decode);
        final Bson filter = cursorOpt
                .map(cursor -> Filters.and(pathFilter(thingId, path, query),
                        keysetFilter(cursor, descending)))
                .orElseGet(() -> pathFilter(thingId, path, query));
        final Optional<Integer> limitOpt = query.getLimit();

        // Use getCollection() directly — do NOT call ensureCollection() on the read path. An
        // authenticated read against a never-written namespace must not provision a new MongoDB
        // Time Series collection (per-namespace collection pollution as an authenticated DoS).
        // MongoDB's find() against a non-existent collection returns an empty cursor without
        // erroring, which is exactly the user-facing semantics we want for "no data yet".
        // Collection creation stays scoped to the write path.
        // Bound both runtime (maxTime) and heap (page size). The page size is the smaller of the
        // caller's limit and the ceiling, so an unbounded range can't OOM the service.
        final int maxPoints = current.config.getMaxQueryResultSize();
        final int pageSize = limitOpt.map(l -> Math.min(l, maxPoints)).orElse(maxPoints);
        final MongoCollection<Document> collection = getCollection(current, thingId);
        final FindPublisher<Document> findPublisher = collection.find(filter)
                // Deterministic total order (timestamp, then revision as tie-breaker) so a keyset
                // cursor resumes exactly after the last returned point — no skipped or repeated
                // same-timestamp points. Descending flips both keys so a `desc` read pages from
                // newest to oldest.
                .sort(orderSort(descending))
                .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                // Fetch one extra row to detect a further page (standard keyset technique); pageSize
                // is capped at maxPoints so pageSize + 1 stays within a safe int range.
                .limit(pageSize + 1);
        return collectAll(findPublisher)
                .thenApply(documents -> {
                    final boolean hasMore = documents.size() > pageSize;
                    final List<Document> page = hasMore ? documents.subList(0, pageSize) : documents;
                    // When the page is capped (by the caller's limit or the ceiling) and more data
                    // matches, emit a cursor so the truncation is explicit and resumable rather than
                    // a silent drop of the remaining points.
                    final String nextCursor = hasMore ? nextCursorFrom(page).encode() : null;
                    return buildResult(thingId, path, query, page, hasMore, nextCursor);
                });
    }

    /** The {@code (timestamp, revision)} sort in the requested direction. */
    private static Bson orderSort(final boolean descending) {
        return descending
                ? Sorts.descending(TimeseriesBsonMapper.FIELD_TIMESTAMP, TimeseriesBsonMapper.FIELD_REVISION)
                : Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP, TimeseriesBsonMapper.FIELD_REVISION);
    }

    /**
     * A keyset predicate keeping only points that sort strictly after {@code cursor} under the
     * {@code (timestamp, revision)} total order used by the raw read: greater-than for ascending
     * reads, less-than for descending ones.
     */
    private static Bson keysetFilter(final TimeseriesCursor cursor, final boolean descending) {
        final Date timestamp = Date.from(cursor.getTimestamp());
        if (descending) {
            return Filters.or(
                    Filters.lt(TimeseriesBsonMapper.FIELD_TIMESTAMP, timestamp),
                    Filters.and(
                            Filters.eq(TimeseriesBsonMapper.FIELD_TIMESTAMP, timestamp),
                            Filters.lt(TimeseriesBsonMapper.FIELD_REVISION, cursor.getRevision())));
        }
        return Filters.or(
                Filters.gt(TimeseriesBsonMapper.FIELD_TIMESTAMP, timestamp),
                Filters.and(
                        Filters.eq(TimeseriesBsonMapper.FIELD_TIMESTAMP, timestamp),
                        Filters.gt(TimeseriesBsonMapper.FIELD_REVISION, cursor.getRevision())));
    }

    /** Builds the cursor pointing just past the last point of a (non-empty) page. */
    private static TimeseriesCursor nextCursorFrom(final List<Document> page) {
        final Document last = page.get(page.size() - 1);
        final Instant timestamp = ((Date) last.get(TimeseriesBsonMapper.FIELD_TIMESTAMP)).toInstant();
        final long revision = ((Number) last.get(TimeseriesBsonMapper.FIELD_REVISION)).longValue();
        return TimeseriesCursor.of(timestamp, revision);
    }

    /**
     * Downsamples one path into {@code step}-sized buckets using a {@code $dateTrunc} + {@code $group}
     * aggregation pipeline, then applies the requested {@link FillStrategy} to interior gaps.
     */
    private CompletionStage<TimeseriesQueryResult> aggregateOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path,
            final Duration step,
            final Aggregation agg) {

        final ThingId thingId = query.getThingId();
        final Bson filter = pathFilter(thingId, path, query);
        final Document dateTrunc =
                dateTruncSpec(step, query.getTimezone().map(Object::toString).orElse(null));
        final BsonField valueAccumulator =
                accumulatorFor(agg, "v", "$" + TimeseriesBsonMapper.FIELD_VALUE);
        final BsonField unitAccumulator =
                Accumulators.first("unit", "$meta." + TimeseriesBsonMapper.META_UNIT);

        final List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        // Pre-sort so $first/$last reflect chronological order rather than storage order.
        pipeline.add(Aggregates.sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP)));
        pipeline.add(Aggregates.group(dateTrunc, valueAccumulator, unitAccumulator));
        pipeline.add(Aggregates.sort(Sorts.ascending("_id")));

        final int maxPoints = current.config.getMaxQueryResultSize();
        final MongoCollection<Document> collection = getCollection(current, thingId);
        if (usesNativeFill(current, query)) {
            appendNativeFillStages(pipeline, step, query.getFillStrategy().orElseThrow(), maxPoints);
            return collectAll(collection.aggregate(pipeline)
                            .allowDiskUse(true)
                            .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                    .thenApplyAsync(buckets -> {
                        // A tiny step over a broad range densifies a huge grid server-side; bound the
                        // rows pulled into service heap (mirrors the raw/derivative scan ceiling).
                        if (buckets.size() > maxPoints) {
                            throw fillGridExceeded(maxPoints, path);
                        }
                        return buildNativelyFilledResult(thingId, path, query, buckets);
                    });
        }
        return collectAll(collection.aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                // Bucket assembly + gap fill is CPU work — keep it off the reactive driver thread.
                .thenApplyAsync(buckets -> buildAggregatedResult(thingId, path, query, step, buckets, maxPoints));
    }

    /**
     * Whether gap-fill should be pushed to MongoDB ({@code $densify}+{@code $fill}) rather than the
     * kernel. Only {@code LINEAR} (&rarr; {@code $fill} method {@code linear}) and {@code PREVIOUS}
     * (&rarr; {@code locf}) map to native methods, and only when the capabilities declare the
     * strategy native. Timezone-aligned queries stay in the kernel because {@code $densify} steps by
     * a fixed duration with no timezone parameter, so a calendar (DST-aware) day grid could diverge;
     * the kernel's {@code nextBucket} handles that correctly. A genuinely-null aggregate value (e.g.
     * single-point {@code STDDEV}) is preserved as a gap via the {@code _wasNull} marker, so no
     * aggregation needs to be excluded.
     */
    private static boolean usesNativeFill(final State current, final TimeseriesQuery query) {

        final FillStrategy fill = query.getFillStrategy().orElse(null);
        return fill != null
                && query.getTimezone().isEmpty()
                && (fill == FillStrategy.LINEAR || fill == FillStrategy.PREVIOUS)
                && current.config.getCapabilities().canFillNatively(fill);
    }

    /**
     * Appends the native gap-fill stages: tag the real buckets with {@code _real}, {@code $densify}
     * the interior grid at {@code step} (bounds {@code "full"} = data min&ndash;max, so only interior
     * gaps are created — matching the kernel), mark rows that were null before filling
     * ({@code _wasNull}), then {@code $fill} the value. A trailing {@code $sort} makes the order
     * deterministic and {@code $limit} bounds the rows transferred to the service heap. Densified
     * rows lack {@code _real} (rendered as gaps); a real row with {@code _wasNull} is a genuine-null
     * bucket and is also a gap — see {@link #buildNativelyFilledResult}.
     */
    private static void appendNativeFillStages(final List<Bson> pipeline, final Duration step,
            final FillStrategy fill, final int maxPoints) {

        final TimeseriesComputeKernel.StepUnit stepUnit = TimeseriesComputeKernel.stepUnitFor(step);
        pipeline.add(new Document("$addFields", new Document("_real", true)));
        pipeline.add(new Document("$densify", new Document("field", "_id")
                .append("range", new Document("step", stepUnit.binSize())
                        .append("unit", stepUnit.unit())
                        .append("bounds", "full"))));
        if (fill == FillStrategy.LINEAR) {
            // $fill linear raises a TypeMismatch on a non-numeric value; coerce non-numeric (or
            // missing) to null so it is filled/treated as a gap — matching the kernel's linearFill,
            // which returns a null gap for non-numeric endpoints rather than erroring.
            pipeline.add(new Document("$addFields", new Document(TimeseriesBsonMapper.FIELD_VALUE,
                    new Document("$cond", Arrays.asList(
                            new Document("$isNumber", "$" + TimeseriesBsonMapper.FIELD_VALUE),
                            "$" + TimeseriesBsonMapper.FIELD_VALUE, null)))));
        }
        // Capture null-ness BEFORE $fill: densified rows and any genuine-null real bucket (e.g. a
        // single-point STDDEV or an all-null AVG). $fill would otherwise fabricate a value for them.
        pipeline.add(new Document("$addFields",
                new Document("_wasNull", new Document("$eq", Arrays.asList("$v", null)))));
        final String method = fill == FillStrategy.LINEAR ? "linear" : "locf";
        pipeline.add(new Document("$fill", new Document("sortBy", new Document("_id", 1))
                .append("output", new Document("v", new Document("method", method)))));
        pipeline.add(Aggregates.sort(Sorts.ascending("_id")));
        pipeline.add(Aggregates.limit(maxPoints + 1));
    }

    /**
     * A filter keeping only numeric-valued points, mirroring the kernel's {@code isNumber} filter so
     * the native window operators ({@code $derivative}/{@code $integral}) do not raise a
     * {@code TypeMismatch} on a string/boolean series (they would surface as HTTP 500).
     */
    private static Bson numericValueFilter() {
        return new Document("$expr",
                new Document("$isNumber", "$" + TimeseriesBsonMapper.FIELD_VALUE));
    }

    /**
     * Builds the result from buckets that MongoDB already gap-filled via {@code $densify}+{@code
     * $fill}. Rows carrying {@code _real=true} are real aggregated buckets; the rest were densified
     * (filled) and are flagged as gaps, so the output matches the kernel's fill exactly.
     */
    // Package-private for unit testing the _real-marker gap reconstruction without a live MongoDB.
    static TimeseriesQueryResult buildNativelyFilledResult(final ThingId thingId,
            final JsonPointer path, final TimeseriesQuery query, final List<Document> buckets) {

        final List<TimeseriesDataValue> data = new ArrayList<>(buckets.size());
        String unit = null;
        for (final Document bucket : buckets) {
            final Object id = bucket.get("_id");
            if (!(id instanceof Date)) {
                continue;
            }
            final Instant t = ((Date) id).toInstant();
            final Document shaped = new Document(TimeseriesBsonMapper.FIELD_TIMESTAMP, id)
                    .append(TimeseriesBsonMapper.FIELD_VALUE, bucket.get("v"));
            // A JSON-null value counts as "no value" (a gap), matching the kernel's toValue: a real
            // bucket with a null aggregate is a gap, not a fabricated null data point.
            final JsonValue value = TimeseriesBsonMapper.toDataValue(shaped).getValue()
                    .filter(jsonValue -> !jsonValue.isNull())
                    .orElse(null);
            if (Boolean.TRUE.equals(bucket.get("_real"))) {
                // A real bucket that was null before $fill (_wasNull) is a gap, not the value $fill
                // fabricated from its neighbours; a real bucket with a value is a real data point.
                final boolean wasNull = Boolean.TRUE.equals(bucket.get("_wasNull"));
                data.add(wasNull || value == null
                        ? TimeseriesDataValue.gap(t, null)
                        : TimeseriesDataValue.of(t, value));
                if (unit == null) {
                    unit = bucket.getString("unit");
                }
            } else {
                data.add(TimeseriesDataValue.gap(t, value)); // densified -> flagged as a gap
            }
        }
        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.of(data.size(), unit, inferAggregatedDataType(data));
        return TimeseriesQueryResult.of(thingId, path, query, meta, data);
    }

    /**
     * Builds the {@code $dateTrunc} expression for a step. The {@code (unit, binSize)} pair is derived
     * from the step duration; {@code timezone} (when set) aligns bucket boundaries to that zone.
     */
    private static Document dateTruncSpec(final Duration step, @Nullable final String timezone) {
        final TimeseriesComputeKernel.StepUnit stepUnit = TimeseriesComputeKernel.stepUnitFor(step);
        final Document spec = new Document("date", "$" + TimeseriesBsonMapper.FIELD_TIMESTAMP)
                .append("unit", stepUnit.unit())
                .append("binSize", stepUnit.binSize());
        if (timezone != null) {
            spec.append("timezone", timezone);
        }
        return new Document("$dateTrunc", spec);
    }

    private static BsonField accumulatorFor(final Aggregation agg, final String field,
            final String valueExpr) {
        return switch (agg) {
            case AVG -> Accumulators.avg(field, valueExpr);
            case MIN -> Accumulators.min(field, valueExpr);
            case MAX -> Accumulators.max(field, valueExpr);
            case SUM -> Accumulators.sum(field, valueExpr);
            case COUNT -> Accumulators.sum(field, 1);
            case FIRST -> Accumulators.first(field, valueExpr);
            case LAST -> Accumulators.last(field, valueExpr);
            case STDDEV -> Accumulators.stdDevSamp(field, valueExpr);
            default -> throw new IllegalStateException("Unsupported group aggregation: " + agg);
        };
    }

    private static TimeseriesQueryResult buildAggregatedResult(final ThingId thingId,
            final JsonPointer path,
            final TimeseriesQuery query,
            final Duration step,
            final List<Document> buckets,
            final int maxPoints) {

        // Bucket-start -> aggregated value, preserving Mongo's $dateTrunc alignment (incl. timezone).
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        String unit = null;
        for (final Document bucket : buckets) {
            final Object id = bucket.get("_id");
            if (!(id instanceof Date)) {
                continue;
            }
            final Document shaped = new Document(TimeseriesBsonMapper.FIELD_TIMESTAMP, id)
                    .append(TimeseriesBsonMapper.FIELD_VALUE, bucket.get("v"));
            // A JSON-null aggregate (e.g. single-point STDDEV) is "no value" -> a gap, matching the
            // kernel. Without the filter, toDataValue yields a present JSON-null which fillBuckets
            // would render as a non-gap value — the round-2 divergence this closes.
            final JsonValue value = TimeseriesBsonMapper.toDataValue(shaped).getValue()
                    .filter(jsonValue -> !jsonValue.isNull())
                    .orElse(null);
            byBucket.put(((Date) id).toInstant(), value);
            if (unit == null) {
                unit = bucket.getString("unit");
            }
        }

        final FillStrategy fill = query.getFillStrategy().orElse(null);
        final ZoneId zone = query.getTimezone().orElse(null);
        if (fill != null) {
            // Fill materialises the entire interior grid in memory; bound it so a tiny step over a
            // broad data span cannot exhaust the heap.
            ensureFillGridWithinCeiling(byBucket, step, maxPoints, path);
        }
        final List<TimeseriesDataValue> data =
                TimeseriesComputeKernel.fillBuckets(byBucket, step, fill, zone);
        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.of(data.size(), unit, inferAggregatedDataType(data));
        return TimeseriesQueryResult.of(thingId, path, query, meta, data);
    }

    /**
     * Refuses (HTTP 400) when a {@code fill} would materialise more than {@code maxPoints} buckets —
     * the populated buckets' span divided by the step (an upper bound on the interior grid).
     */
    private static void ensureFillGridWithinCeiling(final LinkedHashMap<Instant, JsonValue> byBucket,
            final Duration step, final int maxPoints, final JsonPointer path) {

        if (byBucket.isEmpty()) {
            return;
        }
        final List<Instant> keys = new ArrayList<>(byBucket.keySet());
        final long spanSeconds =
                keys.get(keys.size() - 1).getEpochSecond() - keys.get(0).getEpochSecond();
        final long stepSeconds = Math.max(1L, step.getSeconds());
        if (spanSeconds / stepSeconds > maxPoints) {
            throw fillGridExceeded(maxPoints, path);
        }
    }

    private static TimeseriesQueryInvalidException fillGridExceeded(final int maxPoints,
            final JsonPointer path) {

        return TimeseriesQueryInvalidException
                .newBuilder("The gap-filled result for <" + path + "> would exceed the maximum of " +
                        maxPoints + " data points.")
                .description("Increase the 'step', narrow the time range (from/to), or omit 'fill'.")
                .build();
    }

    private static String inferAggregatedDataType(final List<TimeseriesDataValue> data) {
        for (final TimeseriesDataValue v : data) {
            final Optional<String> type = inferDataType(v);
            if (type.isPresent()) {
                return type.get();
            }
        }
        return "number";
    }

    /**
     * Dispatches the window-function-style aggregations that are computed from fetched points rather
     * than a single {@code $group} accumulator: {@code derivative}, {@code rate}, {@code integral},
     * {@code percentile}. Semantics follow the design doc (§7.3).
     */
    private CompletionStage<TimeseriesQueryResult> aggregateAdvancedOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path,
            final Aggregation agg) {

        return switch (agg) {
            case DERIVATIVE -> derivativeOnePath(current, query, path, false);
            case RATE -> derivativeOnePath(current, query, path, true);
            case INTEGRAL -> integralOnePath(current, query, path);
            case PERCENTILE -> percentileOnePath(current, query, path);
            default -> failedStage(new UnsupportedOperationException(
                    "Aggregation <" + agg.getName() + "> is not supported."));
        };
    }

    /**
     * {@code derivative} = (v[n]-v[n-1])/dt (seconds); {@code rate} is the non-negative variant that
     * treats a value decrease as a counter reset ({@code v[n]/dt}). When {@code step} is present the
     * series is first downsampled to the {@code last} value per bucket, then differenced between
     * buckets; otherwise consecutive raw points are differenced. The first point has no predecessor,
     * so the result has one fewer point than the source.
     */
    private CompletionStage<TimeseriesQueryResult> derivativeOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path,
            final boolean rate) {

        final ThingId thingId = query.getThingId();
        // Push-down when allowed: MongoDB 5.0+ computes the derivative natively with the $derivative
        // window operator over the raw points. Only the plain derivative over raw points is pushed
        // down — `rate` (counter-reset) has no native operator, and the stepped (bucket-last) variant
        // stays in the kernel; both fall through to the point-fetch + kernel path below.
        if (!rate && query.getStep().isEmpty()
                && current.config.getCapabilities().canPushDown(Aggregation.DERIVATIVE)) {
            return nativeDerivativeOnePath(current, query, path);
        }
        final CompletionStage<List<TimeseriesComputeKernel.TimePoint>> pointsStage =
                query.getStep().isPresent()
                        ? fetchBucketLast(current, query, path, query.getStep().get())
                        : fetchRawTimedValues(current, query, path, rate ? "rate" : "derivative");
        // Differencing is CPU work over the fetched points — keep it off the reactive driver thread.
        return pointsStage.thenApplyAsync(points -> {
            final List<TimeseriesDataValue> data = TimeseriesComputeKernel.derivative(points, rate);
            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
            return TimeseriesQueryResult.of(thingId, path, query, meta, data);
        });
    }

    /**
     * Native {@code derivative}: a {@code $setWindowFields} pipeline with the {@code $derivative}
     * window operator (per-second) over a 2-point trailing window. The first point has no
     * predecessor and gets a {@code null} derivative, which is filtered out so the shape matches the
     * kernel (one fewer point than the source), each stamped at the later observation.
     */
    private CompletionStage<TimeseriesQueryResult> nativeDerivativeOnePath(final State current,
            final TimeseriesQuery query, final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        final Bson filter = pathFilter(thingId, path, query);
        // A derivative returns ~one row per input point, so bound the number of rows pulled into the
        // service heap to the same ceiling as the kernel path, and refuse (HTTP 400) when it is hit —
        // computing a derivative over a truncated series would drop its tail. The $setWindowFields
        // itself runs server-side (allowDiskUse), so the ceiling caps transfer, not the computation.
        final int maxPoints = current.config.getMaxQueryResultSize();
        final List<Bson> pipeline = List.of(
                Aggregates.match(filter),
                // Keep only numeric points, mirroring the kernel's toTimePoints filter — $derivative
                // raises a TypeMismatch on a non-numeric value (a string series would 500 otherwise).
                Aggregates.match(numericValueFilter()),
                new Document("$setWindowFields", new Document("sortBy",
                        new Document(TimeseriesBsonMapper.FIELD_TIMESTAMP, 1))
                        .append("output", new Document("d", new Document("$derivative",
                                new Document("input", "$" + TimeseriesBsonMapper.FIELD_VALUE)
                                        .append("unit", "second"))
                                .append("window", new Document("documents", List.of(-1, 0)))))),
                Aggregates.match(Filters.ne("d", null)),
                Aggregates.sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP)),
                Aggregates.limit(maxPoints + 1));
        return collectAll(getCollection(current, thingId).aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .thenApply(docs -> {
                    if (docs.size() > maxPoints) {
                        throw scanCeilingExceeded("derivative", maxPoints, path);
                    }
                    final List<TimeseriesDataValue> data = new ArrayList<>(docs.size());
                    for (final Document doc : docs) {
                        final Object t = doc.get(TimeseriesBsonMapper.FIELD_TIMESTAMP);
                        final Object d = doc.get("d");
                        if (t instanceof Date && d instanceof Number number) {
                            data.add(TimeseriesDataValue.of(((Date) t).toInstant(),
                                    JsonValue.of(number.doubleValue())));
                        }
                    }
                    final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
                    return TimeseriesQueryResult.of(thingId, path, query, meta, data);
                });
    }

    /**
     * {@code integral} = trapezoidal area under the curve over consecutive raw points, in
     * value-seconds. A single whole-range result is emitted, stamped at the last observation.
     * (The design doc reports it as {@code result.integral}; here it is the single data point.)
     */
    private CompletionStage<TimeseriesQueryResult> integralOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        if (current.config.getCapabilities().canPushDown(Aggregation.INTEGRAL)) {
            return nativeIntegralOnePath(current, query, path);
        }
        return fetchRawTimedValues(current, query, path, "integral").thenApplyAsync(points -> {
            final List<TimeseriesDataValue> data = TimeseriesComputeKernel.integral(points);
            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
            return TimeseriesQueryResult.of(thingId, path, query, meta, data);
        });
    }

    /**
     * Native {@code integral}: a {@code $setWindowFields} pipeline with the {@code $integral} window
     * operator (value-seconds) accumulated over the whole series; the last document's running total
     * is the result, stamped at the last observation — matching the kernel's trapezoidal integral.
     */
    private CompletionStage<TimeseriesQueryResult> nativeIntegralOnePath(final State current,
            final TimeseriesQuery query, final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        final Bson filter = pathFilter(thingId, path, query);
        final List<Bson> pipeline = List.of(
                Aggregates.match(filter),
                // Keep only numeric points (matches the kernel); $integral TypeMismatches otherwise.
                Aggregates.match(numericValueFilter()),
                new Document("$setWindowFields", new Document("sortBy",
                        new Document(TimeseriesBsonMapper.FIELD_TIMESTAMP, 1))
                        .append("output", new Document("ig", new Document("$integral",
                                new Document("input", "$" + TimeseriesBsonMapper.FIELD_VALUE)
                                        .append("unit", "second"))
                                .append("window", new Document("documents",
                                        List.of("unbounded", "unbounded")))))),
                Aggregates.sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP)),
                Aggregates.group(null,
                        new BsonField("ig", new Document("$last", "$ig")),
                        new BsonField("t", new Document("$last",
                                "$" + TimeseriesBsonMapper.FIELD_TIMESTAMP))));
        return collectAll(getCollection(current, thingId).aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .thenApply(docs -> {
                    final List<TimeseriesDataValue> data = new ArrayList<>(1);
                    if (!docs.isEmpty()) {
                        final Document doc = docs.get(0);
                        final Object t = doc.get("t");
                        final Object ig = doc.get("ig");
                        if (t instanceof Date && ig instanceof Number number) {
                            data.add(TimeseriesDataValue.of(((Date) t).toInstant(),
                                    JsonValue.of(number.doubleValue())));
                        }
                    }
                    final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
                    return TimeseriesQueryResult.of(thingId, path, query, meta, data);
                });
    }

    /**
     * {@code percentile} = the Nth percentile of values per {@code step} bucket (or the whole range
     * when no {@code step} is given). Values are gathered per bucket with {@code $push}; the
     * percentile is computed in memory via linear interpolation, which is portable across MongoDB
     * versions (the {@code $percentile} accumulator is only available on 7.0+).
     */
    private CompletionStage<TimeseriesQueryResult> percentileOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        // Defensive: the model layer (TimeseriesQuery.of) already guarantees a percentile is present
        // for the percentile aggregation, so this maps to a 400 rather than ever surfacing a 500.
        final double p = query.getPercentile().orElseThrow(() -> TimeseriesQueryInvalidException.newBuilder(
                "The percentile aggregation requires a percentile value (0-100).").build());
        final Bson filter = pathFilter(thingId, path, query);
        final Object groupId = query.getStep()
                .map(step -> (Object) dateTruncSpec(step,
                        query.getTimezone().map(Object::toString).orElse(null)))
                .orElse(null);

        // Push-down when the capabilities allow it: MongoDB 7.0+ computes the percentile per bucket
        // with the native $percentile accumulator (approximate / t-digest — faster, but its value can
        // differ slightly from the kernel's exact interpolation). Otherwise gather the values with
        // $push and compute the exact percentile in the kernel.
        final boolean nativePercentile =
                current.config.getCapabilities().canPushDown(Aggregation.PERCENTILE);
        final BsonField accumulator = nativePercentile
                ? new BsonField("p", new Document("$percentile",
                        new Document("input", "$" + TimeseriesBsonMapper.FIELD_VALUE)
                                .append("p", List.of(p / 100.0))
                                .append("method", "approximate")))
                : Accumulators.push("vals", "$" + TimeseriesBsonMapper.FIELD_VALUE);

        final List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.group(groupId, accumulator));
        pipeline.add(Aggregates.sort(Sorts.ascending("_id")));

        // allowDiskUse lifts the 100 MB pipeline memory limit for the $push/$group; the per-bucket
        // value array can still hit the 16 MB BSON document cap for an enormous single bucket — a
        // step bounds it. Percentile sorting runs off the driver thread via thenApplyAsync.
        return collectAll(getCollection(current, thingId).aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)).thenApplyAsync(buckets -> {
            final Optional<Duration> stepOpt = query.getStep();
            if (stepOpt.isPresent()) {
                // Bucketed percentile: assemble bucket-start -> value, then apply the fill strategy —
                // same as the other bucketed aggregations and the kernel reference path, so gap
                // interpolation is not silently dropped.
                final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
                for (final Document bucket : buckets) {
                    final Object id = bucket.get("_id");
                    if (!(id instanceof Date)) {
                        continue;
                    }
                    final Double value = nativePercentile
                            ? nativePercentileValue(bucket)
                            : kernelPercentileValue(bucket, p);
                    byBucket.put(((Date) id).toInstant(), value == null ? null : JsonValue.of(value));
                }
                final List<TimeseriesDataValue> data = TimeseriesComputeKernel.fillBuckets(byBucket,
                        stepOpt.get(), query.getFillStrategy().orElse(null),
                        query.getTimezone().orElse(null));
                final TimeseriesResultMeta meta =
                        TimeseriesResultMeta.of(data.size(), null, inferAggregatedDataType(data));
                return TimeseriesQueryResult.of(thingId, path, query, meta, data);
            }
            // Whole-range percentile: a single value stamped at the range start; fill does not apply.
            final List<TimeseriesDataValue> data = new ArrayList<>(1);
            for (final Document bucket : buckets) {
                final Double value = nativePercentile
                        ? nativePercentileValue(bucket)
                        : kernelPercentileValue(bucket, p);
                if (value != null) {
                    data.add(TimeseriesDataValue.of(query.getFrom(), JsonValue.of(value)));
                }
            }
            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
            return TimeseriesQueryResult.of(thingId, path, query, meta, data);
        });
    }

    /** Reads the value from a native {@code $percentile} bucket (an array with one element). */
    @Nullable
    private static Double nativePercentileValue(final Document bucket) {
        final List<?> percentiles = bucket.get("p", List.class);
        if (percentiles != null && !percentiles.isEmpty() && percentiles.get(0) instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    /** Computes the exact percentile in the kernel from a bucket's {@code $push}ed values. */
    @Nullable
    private static Double kernelPercentileValue(final Document bucket, final double p) {
        final List<?> rawVals = bucket.get("vals", List.class);
        final List<Double> nums = new ArrayList<>();
        if (rawVals != null) {
            for (final Object o : rawVals) {
                if (o instanceof Number number) {
                    nums.add(number.doubleValue());
                }
            }
        }
        return nums.isEmpty() ? null : TimeseriesComputeKernel.percentile(nums, p);
    }

    /**
     * Fetches the raw numeric points for a path (ascending by timestamp) to feed a point-derived
     * aggregation named {@code aggregationLabel} (e.g. {@code "integral"}, {@code "derivative"}).
     * <p>
     * Unlike the raw read path — which may legitimately return a partial, truncated series — an
     * aggregation computed over a <em>truncated</em> series would return a confidently wrong scalar
     * (a clipped {@code integral}, a {@code derivative}/{@code rate} missing its tail) under an
     * HTTP 200. So when the scan reaches the {@code max-query-result-size} ceiling this
     * <em>refuses</em> with a {@link TimeseriesQueryInvalidException} (HTTP 400) instructing the
     * caller to narrow the range or downsample with a {@code step}, rather than silently computing
     * over incomplete data. Detection is conservative (a result of exactly the ceiling is treated as
     * truncated) so an inaccurate answer is never returned; the memory footprint is unchanged from
     * the previous bounded scan.
     */
    private CompletionStage<List<TimeseriesComputeKernel.TimePoint>> fetchRawTimedValues(
            final State current,
            final TimeseriesQuery query,
            final JsonPointer path,
            final String aggregationLabel) {

        final ThingId thingId = query.getThingId();
        final int maxPoints = current.config.getMaxQueryResultSize();
        final Bson filter = pathFilter(thingId, path, query);
        final FindPublisher<Document> find = getCollection(current, thingId).find(filter)
                .sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP))
                .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .limit(maxPoints);
        return collectAll(find).thenApplyAsync(docs -> {
            if (docs.size() >= maxPoints) {
                throw scanCeilingExceeded(aggregationLabel, maxPoints, path);
            }
            final List<TimeseriesComputeKernel.TimePoint> out = new ArrayList<>(docs.size());
            for (final Document d : docs) {
                final Object ts = d.get(TimeseriesBsonMapper.FIELD_TIMESTAMP);
                final Object v = d.get(TimeseriesBsonMapper.FIELD_VALUE);
                if (ts instanceof Date && v instanceof Number) {
                    out.add(new TimeseriesComputeKernel.TimePoint(((Date) ts).toInstant(),
                            ((Number) v).doubleValue()));
                }
            }
            if (out.size() < docs.size()) {
                LOGGER.debug("Skipped {} non-numeric point(s) computing the {} aggregation for " +
                        "thing <{}> path <{}>.", docs.size() - out.size(), aggregationLabel, thingId, path);
            }
            // Scan-volume signal for the memory-bound window aggregations: the number of raw points
            // pulled into the service (which the small result size hides). Pairs with the per-query
            // cost line from logQueryCost to reveal a cheap-looking result that scanned a lot.
            LOGGER.debug("Timeseries {} aggregation scanned {} raw point(s) for thing <{}> path <{}>.",
                    aggregationLabel, docs.size(), thingId, path);
            return out;
        });
    }

    /**
     * Builds the HTTP 400 raised when a point-derived aggregation would have to run over a series
     * that reached the {@code max-query-result-size} scan ceiling — i.e. would be computed over
     * truncated, incomplete data.
     */
    private static TimeseriesQueryInvalidException scanCeilingExceeded(final String aggregationLabel,
            final int maxPoints, final JsonPointer path) {

        return TimeseriesQueryInvalidException
                .newBuilder("The '" + aggregationLabel + "' aggregation for <" + path + "> reached " +
                        "the maximum of " + maxPoints + " scanned data points and cannot be computed " +
                        "accurately over a truncated series.")
                .description("Narrow the time range (from/to), or add a 'step' to downsample the " +
                        "series before aggregating.")
                .build();
    }

    /** Downsamples a path to the {@code last} numeric value per {@code step} bucket, ascending. */
    private CompletionStage<List<TimeseriesComputeKernel.TimePoint>> fetchBucketLast(
            final State current,
            final TimeseriesQuery query,
            final JsonPointer path,
            final Duration step) {

        final Bson filter = pathFilter(query.getThingId(), path, query);
        final Document dateTrunc =
                dateTruncSpec(step, query.getTimezone().map(Object::toString).orElse(null));
        final List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP)));
        pipeline.add(Aggregates.group(dateTrunc,
                Accumulators.last("v", "$" + TimeseriesBsonMapper.FIELD_VALUE)));
        pipeline.add(Aggregates.sort(Sorts.ascending("_id")));
        return collectAll(getCollection(current, query.getThingId()).aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                .thenApplyAsync(docs -> {
                    final List<TimeseriesComputeKernel.TimePoint> out = new ArrayList<>();
                    for (final Document d : docs) {
                        final Object id = d.get("_id");
                        final Object v = d.get("v");
                        if (id instanceof Date && v instanceof Number) {
                            out.add(new TimeseriesComputeKernel.TimePoint(((Date) id).toInstant(),
                                    ((Number) v).doubleValue()));
                        }
                    }
                    return out;
                });
    }

    private static TimeseriesQueryResult buildResult(final ThingId thingId,
            final JsonPointer path,
            final TimeseriesQuery query,
            final List<Document> documents,
            final boolean hasMore,
            @Nullable final String nextCursor) {

        final List<TimeseriesDataValue> data = new ArrayList<>(documents.size());
        String unit = null;
        String dataType = null;
        for (final Document doc : documents) {
            final TimeseriesDataValue value = TimeseriesBsonMapper.toDataValue(doc);
            data.add(value);
            if (unit == null) {
                unit = TimeseriesBsonMapper.getStoredUnit(doc).orElse(null);
            }
            if (dataType == null) {
                dataType = inferDataType(value).orElse(null);
            }
        }
        if (dataType == null) {
            dataType = "null";
        }

        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.of(documents.size(), unit, dataType, hasMore, nextCursor);
        return TimeseriesQueryResult.of(thingId, path, query, meta, data);
    }

    private static Optional<String> inferDataType(final TimeseriesDataValue value) {
        return value.getValue().map(MongoDbTimeseriesAdapter::dataTypeOf);
    }

    private static String dataTypeOf(final JsonValue value) {
        if (value.isNumber()) {
            return "number";
        }
        if (value.isString()) {
            return "string";
        }
        if (value.isBoolean()) {
            return "boolean";
        }
        if (value.isNull()) {
            return "null";
        }
        // Object / array values cannot be ingested (write path rejects them); this branch is a
        // defensive fallback.
        return "object";
    }

    /**
     * Returns the MongoDB collection name used for the Thing's namespace. Collection names are
     * derived as {@code <collection-prefix><sanitized-namespace>} where dots in the namespace are
     * replaced with underscores (MongoDB collection-name compatibility).
     * <p>
     * <b>Operator constraint:</b> two namespaces that differ only in {@code .} versus {@code _}
     * (e.g. {@code foo.bar} and {@code foo_bar}) collide on the same MongoDB collection name. Ditto
     * namespaces in practice use dotted reverse-DNS, so this collision is pathological — keep the
     * naming convention dotted-only and the path is unambiguous.
     */
    static String collectionNameFor(final MongoDbTimeseriesAdapterConfig config,
            final ThingId thingId) {

        final String namespace = thingId.getNamespace();
        return config.getCollectionPrefix() + namespace.replace('.', '_');
    }

    private MongoCollection<Document> getCollection(final State current, final ThingId thingId) {
        final String name = collectionNameFor(current.config, thingId);
        return current.database.getCollection(name, Document.class);
    }

    /**
     * Returns the MongoDB collection for the given Thing, creating it as a native MongoDB
     * Time Series collection on first access. Concurrent callers for the same name share one
     * in-flight create; failed creates are evicted from the cache so a subsequent call can retry.
     * <p>
     * Idempotency note: {@code createCollection} will fail with NamespaceExists if the collection
     * already exists from a previous JVM. We treat that error as success since it just means the
     * collection is already configured the way we want — we still cache the "ensured" state so
     * the listing/check happens at most once per name per JVM.
     */
    private CompletionStage<MongoCollection<Document>> ensureCollection(final State current,
            final ThingId thingId) {

        final String name = collectionNameFor(current.config, thingId);
        final String namespace = thingId.getNamespace();
        final MongoCollection<Document> coll = current.database.getCollection(name, Document.class);
        final CompletionStage<Void> ensure = ensuredCollections.computeIfAbsent(name, n ->
                ensureCollectionConfigured(current, n, namespace)
                        .whenComplete((v, ex) -> {
                            if (ex != null) {
                                ensuredCollections.remove(n);
                            }
                        }));
        return ensure.thenApply(ignored -> coll);
    }

    /**
     * Ensures the per-namespace collection exists and its retention matches the configured
     * (per-namespace, else default) value. A missing collection is created with the right
     * {@code expireAfter}; an existing one is reconciled via {@code collMod} so a changed retention
     * config takes effect without a manual migration. Both branches are idempotent and run at most
     * once per collection per JVM (gated by the {@code ensuredCollections} cache).
     */
    private static CompletionStage<Void> ensureCollectionConfigured(final State current,
            final String name, final String namespace) {

        final Optional<Duration> retention = current.config.getRetention(namespace);
        return collectAllNames(current.database).thenCompose(existing -> {
            if (existing.contains(name)) {
                return reconcileRetention(current, name, retention);
            }
            final TimeSeriesOptions tsOptions = new TimeSeriesOptions(TIME_FIELD)
                    .metaField(META_FIELD)
                    .granularity(toDriverGranularity(current.config.getGranularity()));
            final CreateCollectionOptions opts = new CreateCollectionOptions().timeSeriesOptions(tsOptions);
            retention.map(Duration::toSeconds)
                    .ifPresent(seconds -> opts.expireAfter(seconds, java.util.concurrent.TimeUnit.SECONDS));
            return asVoidStage(current.database.createCollection(name, opts))
                    // Tolerate the "another writer beat us to it" race — the collection now exists.
                    // Reconcile its retention so the loser still converges to the configured value;
                    // other errors propagate so the cache entry is evicted and the caller can retry.
                    .handle((ignored, throwable) -> throwable)
                    .thenCompose(throwable -> {
                        if (throwable == null) {
                            return CompletableFuture.completedFuture(null);
                        }
                        final Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                                && throwable.getCause() != null ? throwable.getCause() : throwable;
                        if (cause instanceof com.mongodb.MongoCommandException mce
                                && (mce.getErrorCode() == 48 || mce.getErrorCode() == 17399)) {
                            return reconcileRetention(current, name, retention);
                        }
                        return CompletableFuture.failedFuture(cause);
                    });
        });
    }

    /**
     * Aligns an existing collection's {@code expireAfter} with {@code retention} via {@code collMod}.
     * A present duration sets {@code expireAfterSeconds}; an empty one passes {@code "off"} to remove
     * any TTL. Idempotent — re-applying the same value is a no-op on the server.
     * <p>
     * Best-effort: retention reconciliation is maintenance, never a precondition for reading or
     * writing data. A failure (e.g. the DB user lacks the {@code collMod} privilege) leaves the
     * existing retention untouched, is logged at WARN, and resolves to success so it cannot fail —
     * or worse, retry-storm — the ingest/read path that triggered the ensure. The next process
     * restart retries it once the cause is fixed.
     */
    private static CompletionStage<Void> reconcileRetention(final State current, final String name,
            final Optional<Duration> retention) {

        final Object expireAfterSeconds = retention.map(d -> (Object) d.toSeconds()).orElse("off");
        final Document collMod = new Document("collMod", name).append("expireAfterSeconds", expireAfterSeconds);
        LOGGER.info("Reconciling timeseries collection <{}> retention to expireAfterSeconds=<{}>.",
                name, expireAfterSeconds);
        return asVoidStage(current.database.runCommand(collMod))
                .exceptionally(throwable -> {
                    final Throwable cause =
                            throwable instanceof java.util.concurrent.CompletionException
                                    && throwable.getCause() != null ? throwable.getCause() : throwable;
                    LOGGER.warn("Failed to reconcile retention for timeseries collection <{}> to " +
                            "expireAfterSeconds=<{}>; leaving the existing retention unchanged and " +
                            "continuing (data flow is unaffected). Cause: {}",
                            name, expireAfterSeconds, cause.getMessage());
                    return null;
                });
    }

    private static CompletionStage<List<String>> collectAllNames(final MongoDatabase database) {
        return collectAll(database.listCollectionNames());
    }

    private static TimeSeriesGranularity toDriverGranularity(final Granularity granularity) {
        return switch (granularity) {
            case SECONDS -> TimeSeriesGranularity.SECONDS;
            case MINUTES -> TimeSeriesGranularity.MINUTES;
            case HOURS -> TimeSeriesGranularity.HOURS;
        };
    }

    /**
     * Visible for tests — allows injecting a mock MongoClient + MongoDatabase, bypassing the real
     * connection setup performed in {@link #initialize}.
     */
    static MongoDbTimeseriesAdapter forTesting(final MongoClient client,
            final MongoDatabase database,
            final MongoDbTimeseriesAdapterConfig config) {

        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();
        adapter.state.set(State.initialized(checkNotNull(config, "config"), client, database));
        return adapter;
    }

    /**
     * Visible for tests — exposes the loaded configuration once {@link #initialize} has completed.
     *
     * @return the active config, or {@code null} if not initialised.
     */
    @Nullable
    MongoDbTimeseriesAdapterConfig getConfigOrNull() {
        return state.get().config;
    }

    /**
     * Drains a {@link Publisher} into a {@link CompletionStage} that completes with the list of
     * emitted items on {@code onComplete} or exceptionally on {@code onError}.
     */
    private static <T> CompletionStage<List<T>> collectAll(final Publisher<T> publisher) {
        final CompletableFuture<List<T>> future = new CompletableFuture<>();
        final List<T> collected = new ArrayList<>();
        publisher.subscribe(new Subscriber<T>() {

            @Override
            public void onSubscribe(final Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final T item) {
                collected.add(item);
            }

            @Override
            public void onError(final Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(Collections.unmodifiableList(collected));
            }
        });
        return future;
    }

    /**
     * Combines a list of {@link CompletionStage}s into a single stage that completes with the
     * results in input order. Failure of any input stage fails the combined stage with the same
     * cause.
     */
    private static <T> CompletionStage<List<T>> collectInOrder(
            final List<CompletionStage<T>> stages) {

        if (stages.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        final CompletableFuture<?>[] array = stages.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array).thenApply(ignored -> {
            final List<T> ordered = new ArrayList<>(stages.size());
            for (final CompletionStage<T> stage : stages) {
                ordered.add(stage.toCompletableFuture().join());
            }
            return Collections.unmodifiableList(ordered);
        });
    }

    /**
     * Drains a single-completion {@link Publisher} into a {@link CompletionStage} that completes
     * with {@code null} on {@code onComplete} or exceptionally on {@code onError}.
     */
    private static CompletionStage<Void> asVoidStage(final Publisher<?> publisher) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        publisher.subscribe(new Subscriber<Object>() {

            @Override
            public void onSubscribe(final Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final Object item) {
                // discard — the operation is fire-and-forget.
            }

            @Override
            public void onError(final Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }
        });
        return future;
    }

    private static <T> CompletionStage<T> failedStage(final Throwable throwable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Snapshot of the adapter's lifecycle state.
     */
    private static final class State {

        private final HealthStatus health;
        @Nullable private final MongoDbTimeseriesAdapterConfig config;
        @Nullable private final MongoClient client;
        @Nullable private final MongoDatabase database;

        private State(final HealthStatus health,
                @Nullable final MongoDbTimeseriesAdapterConfig config,
                @Nullable final MongoClient client,
                @Nullable final MongoDatabase database) {

            this.health = health;
            this.config = config;
            this.client = client;
            this.database = database;
        }

        static State notInitialized() {
            return new State(HealthStatus.DOWN, null, null, null);
        }

        static State initialized(final MongoDbTimeseriesAdapterConfig config,
                final MongoClient client,
                final MongoDatabase database) {

            return new State(HealthStatus.UP, config, client, database);
        }

        static State shutDown() {
            return new State(HealthStatus.DOWN, null, null, null);
        }
    }
}
