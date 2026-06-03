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
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
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
