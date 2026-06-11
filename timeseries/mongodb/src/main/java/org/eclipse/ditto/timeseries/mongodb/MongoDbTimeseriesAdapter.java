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
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
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

    private static Bson pathFilter(final ThingId thingId, final JsonPointer path,
            final TimeseriesQuery query) {
        return Filters.and(
                Filters.eq("meta." + TimeseriesBsonMapper.META_THING_ID, thingId.toString()),
                Filters.eq("meta." + TimeseriesBsonMapper.META_PATH, path.toString()),
                Filters.gte(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(query.getFrom())),
                Filters.lt(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(query.getTo())));
    }

    private CompletionStage<TimeseriesQueryResult> queryOnePath(final State current,
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

    private CompletionStage<TimeseriesQueryResult> queryOnePathRaw(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();

        final Bson filter = pathFilter(thingId, path, query);
        final Optional<Integer> limitOpt = query.getLimit();

        // Use getCollection() directly — do NOT call ensureCollection() on the read path. An
        // authenticated read against a never-written namespace must not provision a new MongoDB
        // Time Series collection (per-namespace collection pollution as an authenticated DoS).
        // MongoDB's find() against a non-existent collection returns an empty cursor without
        // erroring, which is exactly the user-facing semantics we want for "no data yet".
        // Collection creation stays scoped to the write path.
        // Bound both runtime (maxTime) and heap (scan ceiling). The effective limit is the smaller of
        // the caller's limit and MAX_SCAN_POINTS, so an unbounded range can't OOM the service.
        final int maxPoints = current.config.getMaxQueryResultSize();
        final int effectiveLimit = limitOpt.map(l -> Math.min(l, maxPoints)).orElse(maxPoints);
        final MongoCollection<Document> collection = getCollection(current, thingId);
        final FindPublisher<Document> findPublisher = collection.find(filter)
                .sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP))
                .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .limit(effectiveLimit);
        return collectAll(findPublisher)
                .thenApply(documents -> {
                    warnIfScanCeilingHit(documents.size(), limitOpt.orElse(null), maxPoints, thingId, path);
                    return buildResult(thingId, path, query, documents);
                });
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

        final MongoCollection<Document> collection = getCollection(current, thingId);
        return collectAll(collection.aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS))
                // Bucket assembly + gap fill is CPU work — keep it off the reactive driver thread.
                .thenApplyAsync(buckets -> buildAggregatedResult(thingId, path, query, step, buckets));
    }

    /**
     * The {@code (unit, binSize)} pair MongoDB's {@code $dateTrunc} bins by, derived from a step
     * {@link Duration}. The same pair drives the in-memory gap-fill grid (see {@link #nextBucket})
     * so the two stay aligned.
     */
    private record StepUnit(String unit, long binSize) {}

    private static StepUnit stepUnitFor(final Duration step) {
        final long seconds = step.getSeconds();
        if (seconds % 86400 == 0) {
            return new StepUnit("day", seconds / 86400);
        } else if (seconds % 3600 == 0) {
            return new StepUnit("hour", seconds / 3600);
        } else if (seconds % 60 == 0) {
            return new StepUnit("minute", seconds / 60);
        } else {
            return new StepUnit("second", seconds);
        }
    }

    /**
     * Builds the {@code $dateTrunc} expression for a step. The {@code (unit, binSize)} pair is derived
     * from the step duration; {@code timezone} (when set) aligns bucket boundaries to that zone.
     */
    private static Document dateTruncSpec(final Duration step, @Nullable final String timezone) {
        final StepUnit stepUnit = stepUnitFor(step);
        final Document spec = new Document("date", "$" + TimeseriesBsonMapper.FIELD_TIMESTAMP)
                .append("unit", stepUnit.unit())
                .append("binSize", stepUnit.binSize());
        if (timezone != null) {
            spec.append("timezone", timezone);
        }
        return new Document("$dateTrunc", spec);
    }

    /**
     * Advances {@code cursor} to the next bucket start. Without a timezone the step is a fixed
     * {@link Duration} (exact for UTC). With a timezone the step is taken in that zone's calendar, so
     * day/hour boundaries track wall-clock time across DST transitions — mirroring how MongoDB's
     * tz-aware {@code $dateTrunc} aligns buckets (a "day" bucket spans 23h or 25h around a transition,
     * not a fixed 24h). The fill grid is driven off real bucket starts (see {@link #fillBuckets}), so
     * even if a sub-day step's calendar boundary diverges from {@code $dateTrunc} at the transition
     * hour, the grid re-syncs at the next populated bucket rather than dropping it.
     */
    private static Instant nextBucket(final Instant cursor, final Duration step,
            @Nullable final ZoneId zone) {
        if (zone == null) {
            return cursor.plus(step);
        }
        final StepUnit stepUnit = stepUnitFor(step);
        final ZonedDateTime zoned = cursor.atZone(zone);
        final ZonedDateTime next = switch (stepUnit.unit()) {
            case "day" -> zoned.plusDays(stepUnit.binSize());
            case "hour" -> zoned.plusHours(stepUnit.binSize());
            case "minute" -> zoned.plusMinutes(stepUnit.binSize());
            default -> zoned.plusSeconds(stepUnit.binSize());
        };
        return next.toInstant();
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
            final List<Document> buckets) {

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
            final TimeseriesDataValue dv = TimeseriesBsonMapper.toDataValue(shaped);
            byBucket.put(((Date) id).toInstant(), dv.getValue().orElse(null));
            if (unit == null) {
                unit = bucket.getString("unit");
            }
        }

        final FillStrategy fill = query.getFillStrategy().orElse(null);
        final ZoneId zone = query.getTimezone().orElse(null);
        final List<TimeseriesDataValue> data = fillBuckets(byBucket, step, fill, zone);
        final TimeseriesResultMeta meta =
                TimeseriesResultMeta.of(data.size(), unit, inferAggregatedDataType(data));
        return TimeseriesQueryResult.of(thingId, path, query, meta, data);
    }

    /**
     * Materialises the bucket grid. With no fill strategy only populated buckets are emitted; with a
     * strategy, interior gaps between populated buckets are filled per {@link FillStrategy}.
     * <p>
     * The grid is driven off the populated bucket starts rather than a free-running cursor: each
     * {@code [present[i-1], present[i]]} segment is walked by {@link #nextBucket} and always closed by
     * emitting {@code present[i]} exactly. This keeps the grid locked to MongoDB's {@code $dateTrunc}
     * alignment — including tz/DST-aware boundaries — so a populated bucket is never skipped even if a
     * calendar step would otherwise drift around a transition.
     *
     * @param zone the timezone the buckets were aligned to ({@code null} for UTC); controls whether
     * stepping is calendar-aware (see {@link #nextBucket}).
     */
    private static List<TimeseriesDataValue> fillBuckets(
            final LinkedHashMap<Instant, JsonValue> byBucket,
            final Duration step,
            @Nullable final FillStrategy fill,
            @Nullable final ZoneId zone) {

        final List<TimeseriesDataValue> data = new ArrayList<>();
        if (byBucket.isEmpty()) {
            return data;
        }
        final List<Instant> present = new ArrayList<>(byBucket.keySet());
        if (fill == null) {
            for (final Instant bucket : present) {
                data.add(toValue(bucket, byBucket.get(bucket)));
            }
            return data;
        }

        data.add(toValue(present.get(0), byBucket.get(present.get(0))));
        for (int i = 1; i < present.size(); i++) {
            final Instant segmentStart = present.get(i - 1);
            final Instant segmentEnd = present.get(i);
            final JsonValue startValue = byBucket.get(segmentStart);
            final JsonValue endValue = byBucket.get(segmentEnd);
            Instant cursor = nextBucket(segmentStart, step, zone);
            while (cursor.isBefore(segmentEnd)) {
                if (fill == FillStrategy.LINEAR) {
                    data.add(linearFill(cursor, segmentStart, startValue, segmentEnd, endValue));
                } else {
                    data.add(fillValue(cursor, fill, startValue));
                }
                cursor = nextBucket(cursor, step, zone);
            }
            data.add(toValue(segmentEnd, endValue));
        }
        return data;
    }

    private static TimeseriesDataValue toValue(final Instant t, @Nullable final JsonValue v) {
        return v == null ? TimeseriesDataValue.gap(t, null) : TimeseriesDataValue.of(t, v);
    }

    private static TimeseriesDataValue fillValue(final Instant t, final FillStrategy fill,
            @Nullable final JsonValue previous) {
        return switch (fill) {
            case ZERO -> TimeseriesDataValue.gap(t, JsonValue.of(0));
            case PREVIOUS -> TimeseriesDataValue.gap(t, previous);
            case NULL -> TimeseriesDataValue.gap(t, null);
            // LINEAR needs both surrounding anchors, so it is interpolated in fillBuckets directly.
            case LINEAR -> throw new IllegalStateException(
                    "LINEAR fill is interpolated in fillBuckets and must not reach fillValue.");
        };
    }

    /**
     * Linearly interpolates the value at gap instant {@code t} between the surrounding populated
     * buckets {@code (t0, v0)} and {@code (t1, v1)}. Interpolation is only defined for numeric
     * endpoints; for non-numeric or missing neighbours it falls back to a {@code null} gap (the same
     * shape {@link FillStrategy#NULL} would produce) rather than fabricating a value.
     */
    private static TimeseriesDataValue linearFill(final Instant t,
            final Instant t0, @Nullable final JsonValue v0,
            final Instant t1, @Nullable final JsonValue v1) {

        if (v0 == null || v1 == null || !v0.isNumber() || !v1.isNumber()) {
            return TimeseriesDataValue.gap(t, null);
        }
        final double spanMillis = Duration.between(t0, t1).toMillis();
        final double fraction = spanMillis == 0.0 ? 0.0 : Duration.between(t0, t).toMillis() / spanMillis;
        final double y0 = v0.asDouble();
        final double y1 = v1.asDouble();
        return TimeseriesDataValue.gap(t, JsonValue.of(y0 + (y1 - y0) * fraction));
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
        final CompletionStage<List<TimedValue>> pointsStage = query.getStep().isPresent()
                ? fetchBucketLast(current, query, path, query.getStep().get())
                : fetchRawTimedValues(current, query, path);
        // Differencing is CPU work over the fetched points — keep it off the reactive driver thread.
        return pointsStage.thenApplyAsync(points -> {
            final List<TimeseriesDataValue> data = new ArrayList<>();
            for (int i = 1; i < points.size(); i++) {
                final TimedValue prev = points.get(i - 1);
                final TimedValue cur = points.get(i);
                final double dt = (cur.time.toEpochMilli() - prev.time.toEpochMilli()) / 1000.0;
                if (dt <= 0) {
                    continue;
                }
                final double d = (rate && cur.value < prev.value)
                        ? cur.value / dt
                        : (cur.value - prev.value) / dt;
                data.add(TimeseriesDataValue.of(cur.time, JsonValue.of(d)));
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
        return fetchRawTimedValues(current, query, path).thenApplyAsync(points -> {
            double integral = 0;
            for (int i = 1; i < points.size(); i++) {
                final TimedValue prev = points.get(i - 1);
                final TimedValue cur = points.get(i);
                final double dt = (cur.time.toEpochMilli() - prev.time.toEpochMilli()) / 1000.0;
                integral += (cur.value + prev.value) / 2.0 * dt;
            }
            final List<TimeseriesDataValue> data = new ArrayList<>();
            if (!points.isEmpty()) {
                data.add(TimeseriesDataValue.of(points.get(points.size() - 1).time,
                        JsonValue.of(integral)));
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

        final List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(filter));
        pipeline.add(Aggregates.group(groupId,
                Accumulators.push("vals", "$" + TimeseriesBsonMapper.FIELD_VALUE)));
        pipeline.add(Aggregates.sort(Sorts.ascending("_id")));

        // allowDiskUse lifts the 100 MB pipeline memory limit for the $push/$group; the per-bucket
        // value array can still hit the 16 MB BSON document cap for an enormous single bucket — a
        // step bounds it. Percentile sorting runs off the driver thread via thenApplyAsync.
        return collectAll(getCollection(current, thingId).aggregate(pipeline)
                        .allowDiskUse(true)
                        .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)).thenApplyAsync(buckets -> {
            final List<TimeseriesDataValue> data = new ArrayList<>();
            for (final Document bucket : buckets) {
                final Object id = bucket.get("_id");
                final Instant t = (id instanceof Date) ? ((Date) id).toInstant() : query.getFrom();
                final List<?> rawVals = bucket.get("vals", List.class);
                final List<Double> nums = new ArrayList<>();
                if (rawVals != null) {
                    for (final Object o : rawVals) {
                        if (o instanceof Number) {
                            nums.add(((Number) o).doubleValue());
                        }
                    }
                }
                if (!nums.isEmpty()) {
                    data.add(TimeseriesDataValue.of(t, JsonValue.of(computePercentile(nums, p))));
                }
            }
            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
            return TimeseriesQueryResult.of(thingId, path, query, meta, data);
        });
    }

    private static double computePercentile(final List<Double> values, final double p) {
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        final double rank = (p / 100.0) * (sorted.size() - 1);
        final int lo = (int) Math.floor(rank);
        final int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        return sorted.get(lo) + (rank - lo) * (sorted.get(hi) - sorted.get(lo));
    }

    /** Fetches the raw numeric points for a path, ascending by timestamp. */
    private CompletionStage<List<TimedValue>> fetchRawTimedValues(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();
        final int maxPoints = current.config.getMaxQueryResultSize();
        final Bson filter = pathFilter(thingId, path, query);
        final FindPublisher<Document> find = getCollection(current, thingId).find(filter)
                .sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP))
                .maxTime(current.config.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .limit(maxPoints);
        return collectAll(find).thenApplyAsync(docs -> {
            warnIfScanCeilingHit(docs.size(), null, maxPoints, thingId, path);
            final List<TimedValue> out = new ArrayList<>(docs.size());
            for (final Document d : docs) {
                final Object ts = d.get(TimeseriesBsonMapper.FIELD_TIMESTAMP);
                final Object v = d.get(TimeseriesBsonMapper.FIELD_VALUE);
                if (ts instanceof Date && v instanceof Number) {
                    out.add(new TimedValue(((Date) ts).toInstant(), ((Number) v).doubleValue()));
                }
            }
            if (out.size() < docs.size()) {
                LOGGER.debug("Skipped {} non-numeric point(s) computing a window aggregation for " +
                        "thing <{}> path <{}>.", docs.size() - out.size(), thingId, path);
            }
            return out;
        });
    }

    /** Downsamples a path to the {@code last} numeric value per {@code step} bucket, ascending. */
    private CompletionStage<List<TimedValue>> fetchBucketLast(final State current,
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
                    final List<TimedValue> out = new ArrayList<>();
                    for (final Document d : docs) {
                        final Object id = d.get("_id");
                        final Object v = d.get("v");
                        if (id instanceof Date && v instanceof Number) {
                            out.add(new TimedValue(((Date) id).toInstant(), ((Number) v).doubleValue()));
                        }
                    }
                    return out;
                });
    }

    /** Immutable (timestamp, numeric value) pair used by the advanced aggregations. */
    private static final class TimedValue {

        private final Instant time;
        private final double value;

        private TimedValue(final Instant time, final double value) {
            this.time = time;
            this.value = value;
        }
    }

    /**
     * Logs a warning when a read returned exactly the scan ceiling and the caller did not request a
     * smaller {@code limit} — i.e. the result was very likely truncated by the safety cap.
     */
    private static void warnIfScanCeilingHit(final int returned, @Nullable final Integer userLimit,
            final int maxPoints, final ThingId thingId, final JsonPointer path) {

        final boolean cappedByUser = userLimit != null && userLimit < maxPoints;
        if (returned >= maxPoints && !cappedByUser) {
            LOGGER.warn("Timeseries read for thing <{}> path <{}> hit the {}-point scan ceiling and " +
                    "was truncated. Narrow the time range, add a limit, or downsample with a step.",
                    thingId, path, maxPoints);
        }
    }

    private static TimeseriesQueryResult buildResult(final ThingId thingId,
            final JsonPointer path,
            final TimeseriesQuery query,
            final List<Document> documents) {

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

        final TimeseriesResultMeta meta = TimeseriesResultMeta.of(documents.size(), unit, dataType);
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
     */
    private static CompletionStage<Void> reconcileRetention(final State current, final String name,
            final Optional<Duration> retention) {

        final Object expireAfterSeconds = retention.map(d -> (Object) d.toSeconds()).orElse("off");
        final Document collMod = new Document("collMod", name).append("expireAfterSeconds", expireAfterSeconds);
        LOGGER.info("Reconciling timeseries collection <{}> retention to expireAfterSeconds=<{}>.",
                name, expireAfterSeconds);
        return asVoidStage(current.database.runCommand(collMod));
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
