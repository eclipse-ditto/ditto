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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.ConnectionString;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
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

        final MongoClient client;
        final MongoDatabase database;
        try {
            client = MongoClients.create(new ConnectionString(mongoConfig.getUri()));
            database = client.getDatabase(mongoConfig.getDatabase());
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

        // Issue one find() per requested path. The Phase 1 read path is raw — no aggregation, no
        // downsampling — so we collect documents and map them to TimeseriesDataValue in memory.
        // Step / aggregation / fillStrategy on the query are ignored at this phase and will be
        // honoured when the aggregation-pipeline path lands in Phase 2.
        final List<CompletionStage<TimeseriesQueryResult>> perPath = new ArrayList<>(paths.size());
        for (final JsonPointer path : paths) {
            perPath.add(queryOnePath(current, query, path));
        }
        return collectInOrder(perPath);
    }

    private CompletionStage<TimeseriesQueryResult> queryOnePath(final State current,
            final TimeseriesQuery query,
            final JsonPointer path) {

        final ThingId thingId = query.getThingId();

        final Bson filter = Filters.and(
                Filters.eq("meta." + TimeseriesBsonMapper.META_THING_ID, thingId.toString()),
                Filters.eq("meta." + TimeseriesBsonMapper.META_PATH, path.toString()),
                Filters.gte(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(query.getFrom())),
                Filters.lt(TimeseriesBsonMapper.FIELD_TIMESTAMP, Date.from(query.getTo())));
        final Optional<Integer> limitOpt = query.getLimit();

        // Use getCollection() directly — do NOT call ensureCollection() on the read path. An
        // authenticated read against a never-written namespace must not provision a new MongoDB
        // Time Series collection (per-namespace collection pollution as an authenticated DoS).
        // MongoDB's find() against a non-existent collection returns an empty cursor without
        // erroring, which is exactly the user-facing semantics we want for "no data yet".
        // Collection creation stays scoped to the write path.
        final MongoCollection<Document> collection = getCollection(current, thingId);
        FindPublisher<Document> findPublisher = collection.find(filter)
                .sort(Sorts.ascending(TimeseriesBsonMapper.FIELD_TIMESTAMP));
        if (limitOpt.isPresent()) {
            findPublisher = findPublisher.limit(limitOpt.get());
        }
        return collectAll(findPublisher)
                .thenApply(documents -> buildResult(thingId, path, query, documents));
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
        final MongoCollection<Document> coll = current.database.getCollection(name, Document.class);
        final CompletionStage<Void> ensure = ensuredCollections.computeIfAbsent(name, n ->
                createTimeseriesCollectionIfMissing(current, n)
                        .whenComplete((v, ex) -> {
                            if (ex != null) {
                                ensuredCollections.remove(n);
                            }
                        }));
        return ensure.thenApply(ignored -> coll);
    }

    private static CompletionStage<Void> createTimeseriesCollectionIfMissing(final State current,
            final String name) {

        return collectAllNames(current.database).thenCompose(existing -> {
            if (existing.contains(name)) {
                return CompletableFuture.completedFuture(null);
            }
            final TimeSeriesOptions tsOptions = new TimeSeriesOptions(TIME_FIELD)
                    .metaField(META_FIELD)
                    .granularity(toDriverGranularity(current.config.getGranularity()));
            final CreateCollectionOptions opts = new CreateCollectionOptions().timeSeriesOptions(tsOptions);
            current.config.getRetention()
                    .map(java.time.Duration::toSeconds)
                    .ifPresent(seconds -> opts.expireAfter(seconds, java.util.concurrent.TimeUnit.SECONDS));
            return asVoidStage(current.database.createCollection(name, opts))
                    // Tolerate the "another writer beat us to it" race — the collection now exists,
                    // which is the post-condition we wanted. Other errors propagate so the cache
                    // entry is evicted and the caller can retry.
                    .exceptionally(throwable -> {
                        final Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                                && throwable.getCause() != null ? throwable.getCause() : throwable;
                        if (cause instanceof com.mongodb.MongoCommandException mce
                                && (mce.getErrorCode() == 48 || mce.getErrorCode() == 17399)) {
                            return null;
                        }
                        throw new java.util.concurrent.CompletionException(cause);
                    });
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
