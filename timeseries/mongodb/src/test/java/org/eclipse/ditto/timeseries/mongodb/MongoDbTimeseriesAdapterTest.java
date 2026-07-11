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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.ListCollectionNamesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.ConfigFactory;

import org.bson.conversions.Bson;

/**
 * Unit tests for {@link MongoDbTimeseriesAdapter}.
 * <p>
 * Lifecycle, mapping invocation and the MongoDB-driver call shape are exercised against a mocked
 * driver chain (MongoClient → MongoDatabase → MongoCollection); the publishers returned by
 * insertOne / insertMany are stubbed to complete immediately so the adapter's
 * {@code CompletionStage} returns synchronously inside the test.
 */
public final class MongoDbTimeseriesAdapterTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final ThingId OTHER_NAMESPACE_THING = ThingId.of("acme.fleet", "sensor-9");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");

    private MongoDbTimeseriesAdapterConfig config;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> mongoCollection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> otherCollection = mock(MongoCollection.class);

    @Before
    public void setUp() {
        // Adapter never calls into the mongoDbConfig during these tests — initialize is bypassed
        // via forTesting() — so a Mockito mock is sufficient.
        final MongoDbConfig mongoDbConfig = mock(MongoDbConfig.class);
        config = DefaultMongoDbTimeseriesAdapterConfig.of(mongoDbConfig, "ts_", Granularity.SECONDS);

        mongoClient = mock(MongoClient.class);
        mongoDatabase = mock(MongoDatabase.class);
        when(mongoDatabase.getCollection(eq("ts_org_eclipse_ditto"), eq(Document.class)))
                .thenReturn(mongoCollection);
        when(mongoDatabase.getCollection(eq("ts_acme_fleet"), eq(Document.class)))
                .thenReturn(otherCollection);
        // Adapter calls listCollectionNames() / createCollection(...) on first access per
        // namespace to lazily create native MongoDB Time Series collections. Stub them so the
        // unit-test mocks don't NPE — the IT covers the actual MongoDB behaviour.
        @SuppressWarnings("unchecked")
        final ListCollectionNamesPublisher names = mock(ListCollectionNamesPublisher.class);
        doAnswer(invocation -> {
            final Subscriber<? super String> sub = invocation.getArgument(0);
            sub.onSubscribe(new Subscription() {
                @Override public void request(final long n) { sub.onComplete(); }
                @Override public void cancel() { /* no-op */ }
            });
            return null;
        }).when(names).subscribe(any());
        when(mongoDatabase.listCollectionNames()).thenReturn(names);
        when(mongoDatabase.createCollection(any(String.class), any()))
                .thenReturn(completingPublisher(null));
        when(mongoCollection.insertOne(any(Document.class)))
                .thenReturn(completingPublisher(mock(InsertOneResult.class)));
        when(mongoCollection.insertMany(any()))
                .thenReturn(completingPublisher(mock(InsertManyResult.class)));
        when(otherCollection.insertOne(any(Document.class)))
                .thenReturn(completingPublisher(mock(InsertOneResult.class)));
        when(otherCollection.insertMany(any()))
                .thenReturn(completingPublisher(mock(InsertManyResult.class)));
    }

    private MongoDbTimeseriesAdapter newInitialisedAdapter() {
        return MongoDbTimeseriesAdapter.forTesting(mongoClient, mongoDatabase, config);
    }

    // --- Lifecycle ---

    @Test
    public void healthIsDownBeforeInitialize() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void healthIsUpAfterForTesting() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.UP);
    }

    @Test
    public void shutdownTransitionsToDown() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.shutdown().toCompletableFuture().join();

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void shutdownClosesMongoClient() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.shutdown().toCompletableFuture().join();

        verify(mongoClient).close();
    }

    @Test
    public void shutdownIsIdempotent() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.shutdown().toCompletableFuture().join();
        adapter.shutdown().toCompletableFuture().join();

        verify(mongoClient, atLeast(1)).close();
        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void initializeRejectsNullConfig() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        assertThatNullPointerException().isThrownBy(() -> adapter.initialize(null));
    }

    @Test
    public void initializeRejectsWrongConfigType() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();
        final TimeseriesAdapterConfig wrongType = new TimeseriesAdapterConfig() { };

        assertThatExceptionOfType(ExecutionException.class)
                .isThrownBy(() -> adapter.initialize(wrongType).toCompletableFuture().get())
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    // --- Write path ---

    @Test
    public void writeRoutesToCorrectCollectionByNamespace() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.write(sampleDataPoint(THING_ID)).toCompletableFuture().join();

        verify(mongoDatabase).getCollection("ts_org_eclipse_ditto", Document.class);
    }

    @Test
    public void writeInsertsExpectedDocument() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.write(sampleDataPoint(THING_ID)).toCompletableFuture().join();

        final ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoCollection).insertOne(captor.capture());
        final Document inserted = captor.getValue();
        final Document meta = (Document) inserted.get(TimeseriesBsonMapper.FIELD_META);
        assertThat(meta.getString(TimeseriesBsonMapper.META_THING_ID)).isEqualTo(THING_ID.toString());
        assertThat(meta.getString(TimeseriesBsonMapper.META_PATH)).isEqualTo(PATH.toString());
        assertThat(inserted.get(TimeseriesBsonMapper.FIELD_VALUE)).isEqualTo(23.5);
    }

    @Test
    public void writeFailsWhenAdapterNotInitialised() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.write(sampleDataPoint(THING_ID))
                        .toCompletableFuture().join())
                .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void writePropagatesPublisherError() {
        final RuntimeException backendError = new RuntimeException("backend down");
        when(mongoCollection.insertOne(any(Document.class)))
                .thenReturn(failingPublisher(backendError));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.write(sampleDataPoint(THING_ID))
                        .toCompletableFuture().join())
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining("backend down");
    }

    // Note: complex-value rejection at the write-path used to live here. Since IOT-495 the
    // invariant is enforced at the model layer (ImmutableTimeseriesDataPoint.of), so the data
    // point can't be constructed at all — the model's own test covers it
    // (ImmutableTimeseriesDataPointTest#factoryRejectsObjectValue / ...RejectsArrayValue).

    @Test
    public void writeRejectsNullDataPoint() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        assertThatNullPointerException().isThrownBy(() -> adapter.write(null));
    }

    @Test
    public void writeSucceedsEvenWhenRetentionReconcileFails() {
        // The collection already exists -> the ensure path reconciles retention via collMod. Make
        // collMod (runCommand) fail, as it does when the DB user lacks the privilege, and assert the
        // write still completes: retention reconciliation is best-effort and must never break — or
        // retry-storm — the data path.
        stubListCollectionNamesReturning("ts_org_eclipse_ditto");
        when(mongoDatabase.runCommand(any(Bson.class)))
                .thenReturn(failingPublisher(new RuntimeException("not authorized to execute command collMod")));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.write(sampleDataPoint(THING_ID)).toCompletableFuture().join();

        verify(mongoCollection).insertOne(any(Document.class));
    }

    // --- Write-batch path ---

    @Test
    public void writeBatchInsertsManyIntoSingleCollection() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();
        final List<TimeseriesDataPoint> batch = Arrays.asList(
                sampleDataPoint(THING_ID), sampleDataPoint(THING_ID));

        adapter.writeBatch(batch).toCompletableFuture().join();

        verify(mongoCollection).insertMany(any());
    }

    @Test
    public void writeBatchGroupsByCollection() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();
        final List<TimeseriesDataPoint> batch = Arrays.asList(
                sampleDataPoint(THING_ID),
                sampleDataPoint(OTHER_NAMESPACE_THING),
                sampleDataPoint(THING_ID));

        adapter.writeBatch(batch).toCompletableFuture().join();

        verify(mongoCollection).insertMany(any());
        verify(otherCollection).insertMany(any());
    }

    @Test
    public void writeBatchEmptyListIsNoOp() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.writeBatch(Collections.emptyList()).toCompletableFuture().join();

        verify(mongoCollection, org.mockito.Mockito.never()).insertMany(any());
    }

    @Test
    public void writeBatchFailsWhenAdapterNotInitialised() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.writeBatch(
                        Collections.singletonList(sampleDataPoint(THING_ID)))
                        .toCompletableFuture().join())
                .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void writeBatchRejectsNullList() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        assertThatNullPointerException().isThrownBy(() -> adapter.writeBatch(null));
    }

    @Test
    public void writeBatchRejectsNullElement() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();
        final List<TimeseriesDataPoint> withNull = Arrays.asList(sampleDataPoint(THING_ID), null);

        assertThatNullPointerException().isThrownBy(() -> adapter.writeBatch(withNull));
    }

    // --- Query path ---

    @Test
    public void queryReturnsOneResultPerRequestedPath() {
        stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.0)),
                docFor(Instant.parse("2026-01-14T10:01:00Z"), JsonValue.of(22.5)));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        final List<org.eclipse.ditto.timeseries.model.TimeseriesQueryResult> results =
                adapter.query(buildQuery()).toCompletableFuture().join();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getData()).hasSize(2);
    }

    @Test
    public void queryReturnsEmptyDataWhenNoMatchingDocuments() {
        stubFindReturning(mongoCollection /* no docs */);
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        final List<org.eclipse.ditto.timeseries.model.TimeseriesQueryResult> results =
                adapter.query(buildQuery()).toCompletableFuture().join();

        assertThat(results.get(0).getData()).isEmpty();
        assertThat(results.get(0).getMeta().getCount()).isZero();
        assertThat(results.get(0).getMeta().getDataType()).isEqualTo("null");
    }

    @Test
    public void queryAppliesLimitWhenSet() {
        final FindPublisher<org.bson.Document> finder = stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.0)));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();
        final org.eclipse.ditto.timeseries.model.TimeseriesQuery query = limitedQuery(5);

        adapter.query(query).toCompletableFuture().join();

        verify(finder).limit(5);
    }

    @Test
    public void queryAppliesScanCeilingWhenNoLimitSet() {
        final FindPublisher<org.bson.Document> finder = stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(22.0)));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.query(buildQuery()).toCompletableFuture().join();

        // No caller limit -> the configured safety ceiling is applied so an over-broad range
        // cannot exhaust the heap.
        verify(finder).limit(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_MAX_QUERY_RESULT_SIZE);
    }

    @Test
    public void integralRefusesWhenScanCeilingReached() {
        // A raw read may return a partial series, but an integral over a truncated series would be a
        // confidently wrong scalar. So on hitting the scan ceiling the adapter refuses (HTTP 400)
        // rather than computing over incomplete data.
        final MongoDbTimeseriesAdapter adapter = newAdapterWithMaxResultSize(3);
        stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(1.0)),
                docFor(Instant.parse("2026-01-14T10:00:01Z"), JsonValue.of(2.0)),
                docFor(Instant.parse("2026-01-14T10:00:02Z"), JsonValue.of(3.0)));

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.query(advancedQuery(Aggregation.INTEGRAL))
                        .toCompletableFuture().join())
                .withCauseInstanceOf(TimeseriesQueryInvalidException.class);
    }

    @Test
    public void derivativeWithoutStepRefusesWhenScanCeilingReached() {
        final MongoDbTimeseriesAdapter adapter = newAdapterWithMaxResultSize(2);
        stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(1.0)),
                docFor(Instant.parse("2026-01-14T10:00:01Z"), JsonValue.of(2.0)));

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.query(advancedQuery(Aggregation.DERIVATIVE))
                        .toCompletableFuture().join())
                .withCauseInstanceOf(TimeseriesQueryInvalidException.class);
    }

    @Test
    public void integralSucceedsBelowScanCeiling() {
        // Below the ceiling the aggregation is computed normally — the refusal must not over-fire.
        final MongoDbTimeseriesAdapter adapter = newAdapterWithMaxResultSize(5);
        stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(2.0)),
                docFor(Instant.parse("2026-01-14T10:00:10Z"), JsonValue.of(4.0)));

        final List<org.eclipse.ditto.timeseries.model.TimeseriesQueryResult> results =
                adapter.query(advancedQuery(Aggregation.INTEGRAL)).toCompletableFuture().join();

        assertThat(results.get(0).getData()).hasSize(1);
    }

    @Test
    public void queryReturnsEmptyResultListForEmptyPaths() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();
        final org.eclipse.ditto.timeseries.model.TimeseriesQuery emptyPaths =
                org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(
                        THING_ID, Collections.emptyList(),
                        Instant.parse("2026-01-14T00:00:00Z"),
                        Instant.parse("2026-01-15T00:00:00Z"));

        final List<org.eclipse.ditto.timeseries.model.TimeseriesQueryResult> results =
                adapter.query(emptyPaths).toCompletableFuture().join();

        assertThat(results).isEmpty();
        verify(mongoCollection, never()).find(any(Bson.class));
    }

    @Test
    public void queryInfersDataTypeFromFirstValue() {
        stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of("ok")));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        final List<org.eclipse.ditto.timeseries.model.TimeseriesQueryResult> results =
                adapter.query(buildQuery()).toCompletableFuture().join();

        assertThat(results.get(0).getMeta().getDataType()).isEqualTo("string");
    }

    @Test
    public void queryFailsWhenAdapterNotInitialised() {
        final MongoDbTimeseriesAdapter adapter = new MongoDbTimeseriesAdapter();

        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> adapter.query(buildQuery()).toCompletableFuture().join())
                .withCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void queryRejectsNullQuery() {
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        assertThatNullPointerException().isThrownBy(() -> adapter.query(null));
    }

    // --- scan (planner primitive) ---

    @Test
    public void scanReturnsOrderedPointsForRange() {
        final FindPublisher<org.bson.Document> finder = stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(1.0)),
                docFor(Instant.parse("2026-01-14T10:00:01Z"), JsonValue.of(2.0)));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        final List<TimeseriesDataValue> points = adapter.scan(THING_ID, PATH,
                        Instant.parse("2026-01-14T00:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"), 0)
                .toCompletableFuture().join();

        assertThat(points).hasSize(2);
        assertThat(points.get(0).getValue().orElseThrow().asDouble()).isEqualTo(1.0);
        // A non-positive limit falls back to the configured scan ceiling.
        verify(finder).limit(DefaultMongoDbTimeseriesAdapterConfig.DEFAULT_MAX_QUERY_RESULT_SIZE);
    }

    @Test
    public void scanAppliesCallerLimit() {
        final FindPublisher<org.bson.Document> finder = stubFindReturning(mongoCollection,
                docFor(Instant.parse("2026-01-14T10:00:00Z"), JsonValue.of(1.0)));
        final MongoDbTimeseriesAdapter adapter = newInitialisedAdapter();

        adapter.scan(THING_ID, PATH, Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"), 7).toCompletableFuture().join();

        verify(finder).limit(7);
    }

    // --- Native fill result mapping (_real-marker gap reconstruction) ---

    @Test
    public void buildNativelyFilledResultFlagsDensifiedRowsAsGaps() {
        final Instant t0 = Instant.parse("2026-01-14T00:00:00Z");
        final List<org.bson.Document> buckets = Arrays.asList(
                bucketDoc(t0, 10.0, true, "degC"),
                bucketDoc(t0.plusSeconds(2), 13.0, true, "degC"),
                bucketDoc(t0.plusSeconds(4), 20.25, false, null),  // densified -> gap
                bucketDoc(t0.plusSeconds(6), 42.0, true, "degC"));

        final org.eclipse.ditto.timeseries.model.TimeseriesQueryResult result =
                MongoDbTimeseriesAdapter.buildNativelyFilledResult(THING_ID, PATH, buildQuery(), buckets);
        final List<TimeseriesDataValue> data = result.getData();

        assertThat(data).hasSize(4);
        assertThat(data.get(0).isGap()).isFalse();
        assertThat(data.get(2).isGap()).isTrue();
        assertThat(data.get(2).getValue().orElseThrow().asDouble()).isEqualTo(20.25);
        assertThat(data.get(3).isGap()).isFalse();
        assertThat(result.getMeta().getUnit()).contains("degC");
    }

    @Test
    public void buildNativelyFilledResultKeepsNullRealBucketAsGap() {
        final Instant t0 = Instant.parse("2026-01-14T00:00:00Z");

        final org.eclipse.ditto.timeseries.model.TimeseriesQueryResult result =
                MongoDbTimeseriesAdapter.buildNativelyFilledResult(THING_ID, PATH, buildQuery(),
                        Arrays.asList(bucketDoc(t0, null, true, "degC")));

        final TimeseriesDataValue dataValue = result.getData().get(0);
        assertThat(dataValue.isGap()).isTrue();          // a genuine-null real bucket stays a gap
        assertThat(dataValue.getValue()).isEmpty();
    }

    @Test
    public void buildNativelyFilledResultTreatsWasNullRealBucketAsGap() {
        final Instant t0 = Instant.parse("2026-01-14T00:00:00Z");
        // A real bucket whose aggregate was null but $fill fabricated a value from its neighbours:
        // the _wasNull marker must keep it a gap, not emit the fabricated value.
        final org.bson.Document nullReal = new org.bson.Document("_id", java.util.Date.from(t0))
                .append("v", 25.0)
                .append("_real", Boolean.TRUE)
                .append("_wasNull", Boolean.TRUE)
                .append("unit", "degC");

        final org.eclipse.ditto.timeseries.model.TimeseriesQueryResult result =
                MongoDbTimeseriesAdapter.buildNativelyFilledResult(THING_ID, PATH, buildQuery(),
                        Arrays.asList(nullReal));

        final TimeseriesDataValue dataValue = result.getData().get(0);
        assertThat(dataValue.isGap()).isTrue();
        assertThat(dataValue.getValue()).isEmpty();
    }

    private static org.bson.Document bucketDoc(final Instant t, final Double value, final boolean real,
            final String unit) {
        final org.bson.Document doc = new org.bson.Document("_id", java.util.Date.from(t))
                .append("v", value);
        if (real) {
            doc.append("_real", Boolean.TRUE);
        }
        if (unit != null) {
            doc.append("unit", unit);
        }
        return doc;
    }

    // --- Capabilities ---

    @Test
    public void declaresPushDownCapabilities() {
        final Capabilities caps = newInitialisedAdapter().capabilities();

        assertThat(caps.supportsNativeQuery()).isTrue(); // query(...) is a complete executor
        // Group aggregations plus derivative/integral (exact native operators) push down...
        assertThat(caps.canPushDown(Aggregation.AVG)).isTrue();
        assertThat(caps.canPushDown(Aggregation.STDDEV)).isTrue();
        assertThat(caps.canPushDown(Aggregation.DERIVATIVE)).isTrue();
        assertThat(caps.canPushDown(Aggregation.INTEGRAL)).isTrue();
        // ...percentile is opt-in (native $percentile is approximate).
        assertThat(caps.canPushDown(Aggregation.PERCENTILE)).isFalse();
        // linear/previous fill are native by default.
        assertThat(caps.getNativeFillStrategies())
                .containsExactlyInAnyOrder(FillStrategy.LINEAR, FillStrategy.PREVIOUS);
    }

    // --- Collection-name derivation ---

    @Test
    public void collectionNameForReplacesDotsInNamespace() {
        final String name = MongoDbTimeseriesAdapter.collectionNameFor(config, THING_ID);

        assertThat(name).isEqualTo("ts_org_eclipse_ditto");
    }

    @Test
    public void collectionNameForUsesConfiguredPrefix() {
        final MongoDbTimeseriesAdapterConfig customPrefix = DefaultMongoDbTimeseriesAdapterConfig.of(
                mock(MongoDbConfig.class), "x_", Granularity.SECONDS);

        final String name =
                MongoDbTimeseriesAdapter.collectionNameFor(customPrefix, THING_ID);

        assertThat(name).isEqualTo("x_org_eclipse_ditto");
    }

    // --- Helpers ---

    private static org.eclipse.ditto.timeseries.model.TimeseriesQuery buildQuery() {
        return org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"));
    }

    private MongoDbTimeseriesAdapter newAdapterWithMaxResultSize(final int max) {
        // Exclude derivative/integral from push-down so these ops take the kernel (scan) path where
        // the max-query-result-size ceiling and its refusal apply — the native window operators run
        // server-side and have their own (separate) bound.
        final MongoDbTimeseriesAdapterConfig small = DefaultMongoDbTimeseriesAdapterConfig.of(
                mock(MongoDbConfig.class),
                ConfigFactory.parseString("max-query-result-size = " + max + "\n"
                        + "capabilities { pushable-aggregations = [\"avg\"], native-fill-strategies = [] }"));
        return MongoDbTimeseriesAdapter.forTesting(mongoClient, mongoDatabase, small);
    }

    private static org.eclipse.ditto.timeseries.model.TimeseriesQuery advancedQuery(
            final Aggregation aggregation) {
        return org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"),
                null, aggregation, null, null, null);
    }

    private static org.eclipse.ditto.timeseries.model.TimeseriesQuery limitedQuery(final int limit) {
        return org.eclipse.ditto.timeseries.model.TimeseriesQuery.of(
                THING_ID,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"),
                null, null, null, limit, null);
    }

    private static org.bson.Document docFor(final Instant timestamp, final JsonValue value) {
        return TimeseriesBsonMapper.toDocument(TimeseriesDataPoint.of(
                THING_ID, PATH, timestamp, value, 1L, Collections.emptyMap(), null));
    }

    @SuppressWarnings("unchecked")
    private static FindPublisher<org.bson.Document> stubFindReturning(
            final MongoCollection<org.bson.Document> collection,
            final org.bson.Document... docs) {

        final FindPublisher<org.bson.Document> finder = mock(FindPublisher.class);
        when(finder.sort(any())).thenReturn(finder);
        when(finder.maxTime(anyLong(), any(java.util.concurrent.TimeUnit.class))).thenReturn(finder);
        when(finder.limit(anyInt())).thenReturn(finder);
        doAnswer(invocation -> {
            final org.reactivestreams.Subscriber<org.bson.Document> sub = invocation.getArgument(0);
            sub.onSubscribe(new org.reactivestreams.Subscription() {
                private boolean delivered = false;

                @Override
                public void request(final long n) {
                    if (delivered) {
                        return;
                    }
                    delivered = true;
                    for (final org.bson.Document d : docs) {
                        sub.onNext(d);
                    }
                    sub.onComplete();
                }

                @Override
                public void cancel() { }
            });
            return null;
        }).when(finder).subscribe(any(org.reactivestreams.Subscriber.class));
        when(collection.find(any(Bson.class))).thenReturn(finder);
        return finder;
    }

    private void stubListCollectionNamesReturning(final String... names) {
        @SuppressWarnings("unchecked")
        final ListCollectionNamesPublisher publisher = mock(ListCollectionNamesPublisher.class);
        doAnswer(invocation -> {
            final Subscriber<? super String> sub = invocation.getArgument(0);
            sub.onSubscribe(new Subscription() {
                private boolean delivered = false;

                @Override
                public void request(final long n) {
                    if (delivered) {
                        return;
                    }
                    delivered = true;
                    for (final String name : names) {
                        sub.onNext(name);
                    }
                    sub.onComplete();
                }

                @Override
                public void cancel() { /* no-op */ }
            });
            return null;
        }).when(publisher).subscribe(any());
        when(mongoDatabase.listCollectionNames()).thenReturn(publisher);
    }

    private static TimeseriesDataPoint sampleDataPoint(final ThingId thingId) {
        return TimeseriesDataPoint.of(
                thingId,
                PATH,
                Instant.parse("2026-01-15T10:30:00Z"),
                JsonValue.of(23.5),
                42L,
                Collections.emptyMap(),
                null);
    }

    private static <T> Publisher<T> completingPublisher(final T value) {
        return s -> s.onSubscribe(new Subscription() {
            private boolean delivered = false;

            @Override
            public void request(final long n) {
                if (delivered) {
                    return;
                }
                delivered = true;
                s.onNext(value);
                s.onComplete();
            }

            @Override
            public void cancel() {
                // no-op
            }
        });
    }

    private static <T> Publisher<T> failingPublisher(final Throwable error) {
        return s -> s.onSubscribe(new Subscription() {
            private boolean delivered = false;

            @Override
            public void request(final long n) {
                if (delivered) {
                    return;
                }
                delivered = true;
                s.onError(error);
            }

            @Override
            public void cancel() {
                // no-op
            }
        });
    }

    @SuppressWarnings("unused")
    private static <T> Subscriber<T> noopSubscriber() {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(final Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(final T t) { }
            @Override
            public void onError(final Throwable t) { }
            @Override
            public void onComplete() { }
        };
    }
}
