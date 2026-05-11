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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.mongodb.ConnectionString;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Integration test for {@link MongoDbTimeseriesAdapter} against a real MongoDB instance.
 * <p>
 * Skipped by default. Set the environment variable
 * {@code TIMESERIES_MONGODB_TEST_URI=mongodb://localhost:27017} (or whichever URI points at your
 * dev MongoDB) to enable. Each test uses a fresh randomly-named demo Thing namespace so test runs
 * do not collide; the test database is dropped in {@code @AfterClass}.
 */
public final class MongoDbTimeseriesAdapterIT {

    private static final String ENV_VAR = "TIMESERIES_MONGODB_TEST_URI";
    private static final String DATABASE = "ditto_ts_it";
    private static final JsonPointer PATH =
            JsonPointer.of("/features/environment/properties/temperature");

    private static String uri;
    private MongoDbTimeseriesAdapter adapter;
    private ThingId thingId;

    @BeforeClass
    public static void resolveUri() {
        uri = System.getenv(ENV_VAR);
        Assume.assumeTrue(
                "Set " + ENV_VAR + "=mongodb://localhost:27017 (or your dev URI) to enable this IT",
                uri != null && !uri.isEmpty());
    }

    @Before
    public void setUp() throws Exception {
        adapter = new MongoDbTimeseriesAdapter();
        // Random suffix lives in the name part (which permits digits and hyphens); the namespace
        // is fixed and follows Ditto's namespace convention.
        thingId = ThingId.of("it.timeseries.test",
                "sensor-" + UUID.randomUUID().toString().substring(0, 8));

        final MongoDbTimeseriesAdapterConfig config = DefaultMongoDbTimeseriesAdapterConfig.of(
                uri, DATABASE, "ts_", Granularity.SECONDS);
        adapter.initialize(config).toCompletableFuture().get();
    }

    @After
    public void tearDown() throws Exception {
        if (adapter != null) {
            adapter.shutdown().toCompletableFuture().get();
        }
    }

    @AfterClass
    public static void dropTestDatabase() throws Exception {
        if (uri == null || uri.isEmpty()) {
            return;
        }
        final MongoClient client = MongoClients.create(new ConnectionString(uri));
        try {
            blockUntilComplete(client.getDatabase(DATABASE).drop());
        } finally {
            client.close();
        }
    }

    @Test
    public void writeThenQueryReturnsTheStoredDataPoint() throws Exception {
        final TimeseriesDataPoint dp = dataPoint(Instant.parse("2026-01-14T10:00:00Z"), 22.5);

        adapter.write(dp).toCompletableFuture().get();

        final List<TimeseriesQueryResult> results = adapter.query(buildQuery(
                Instant.parse("2026-01-14T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"))).toCompletableFuture().get();

        assertThat(results).hasSize(1);
        final TimeseriesQueryResult result = results.get(0);
        assertThat(result.getData()).hasSize(1);
        final TimeseriesDataValue value = result.getData().get(0);
        assertThat(value.getTimestamp()).isEqualTo(dp.getTimestamp());
        assertThat(value.getValue()).contains(JsonValue.of(22.5));
    }

    @Test
    public void writeBatchPersistsAllDataPointsInChronologicalOrder() throws Exception {
        final List<TimeseriesDataPoint> batch = new ArrayList<>();
        Instant t = Instant.parse("2026-01-14T10:00:00Z");
        final double[] values = {22.0, 22.5, 23.0, 23.5, 24.0};
        for (final double v : values) {
            batch.add(dataPoint(t, v));
            t = t.plus(5, ChronoUnit.MINUTES);
        }

        adapter.writeBatch(batch).toCompletableFuture().get();

        final List<TimeseriesQueryResult> results = adapter.query(buildQuery(
                Instant.parse("2026-01-14T09:00:00Z"),
                Instant.parse("2026-01-14T11:00:00Z"))).toCompletableFuture().get();

        assertThat(results.get(0).getData()).hasSize(values.length);
        for (int i = 0; i < values.length; i++) {
            assertThat(results.get(0).getData().get(i).getValue()).contains(JsonValue.of(values[i]));
        }
    }

    @Test
    public void queryEmptyTimeRangeReturnsEmptyData() throws Exception {
        adapter.write(dataPoint(Instant.parse("2026-01-14T10:00:00Z"), 22.5))
                .toCompletableFuture().get();

        final List<TimeseriesQueryResult> results = adapter.query(buildQuery(
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-02T00:00:00Z"))).toCompletableFuture().get();

        assertThat(results.get(0).getData()).isEmpty();
        assertThat(results.get(0).getMeta().getCount()).isZero();
    }

    @Test
    public void queryAppliesLimitWhenSet() throws Exception {
        final List<TimeseriesDataPoint> batch = new ArrayList<>();
        Instant t = Instant.parse("2026-01-14T10:00:00Z");
        for (int i = 0; i < 10; i++) {
            batch.add(dataPoint(t, 20.0 + i));
            t = t.plus(1, ChronoUnit.MINUTES);
        }
        adapter.writeBatch(batch).toCompletableFuture().get();

        final TimeseriesQuery limitedQuery = TimeseriesQuery.of(
                thingId,
                Collections.singletonList(PATH),
                Instant.parse("2026-01-14T09:00:00Z"),
                Instant.parse("2026-01-14T11:00:00Z"),
                null, null, null, 3, null);

        final List<TimeseriesQueryResult> results =
                adapter.query(limitedQuery).toCompletableFuture().get();

        assertThat(results.get(0).getData()).hasSize(3);
    }

    @Test
    public void queryReturnsOneResultPerPath() throws Exception {
        final JsonPointer humidityPath = JsonPointer.of("/features/environment/properties/humidity");
        final Instant ts = Instant.parse("2026-01-14T10:00:00Z");

        adapter.writeBatch(Arrays.asList(
                dataPoint(ts, 22.5, PATH),
                dataPoint(ts, 65.2, humidityPath))).toCompletableFuture().get();

        final TimeseriesQuery multiPath = TimeseriesQuery.of(
                thingId,
                Arrays.asList(PATH, humidityPath),
                Instant.parse("2026-01-14T09:00:00Z"),
                Instant.parse("2026-01-14T11:00:00Z"));

        final List<TimeseriesQueryResult> results =
                adapter.query(multiPath).toCompletableFuture().get();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getData()).hasSize(1);
        assertThat(results.get(1).getData()).hasSize(1);
        assertThat(results.get(0).getData().get(0).getValue()).contains(JsonValue.of(22.5));
        assertThat(results.get(1).getData().get(0).getValue()).contains(JsonValue.of(65.2));
    }

    @Test
    public void healthIsUpAfterInitialise() {
        assertThat(adapter.getHealth())
                .isEqualTo(org.eclipse.ditto.timeseries.api.HealthStatus.UP);
    }

    private TimeseriesQuery buildQuery(final Instant from, final Instant to) {
        return TimeseriesQuery.of(
                thingId, Collections.singletonList(PATH), from, to);
    }

    private TimeseriesDataPoint dataPoint(final Instant timestamp, final double value) {
        return dataPoint(timestamp, value, PATH);
    }

    private TimeseriesDataPoint dataPoint(final Instant timestamp, final double value,
            final JsonPointer path) {

        final Map<String, String> tags = new LinkedHashMap<>();
        tags.put("attributes/building", "A");
        tags.put("attributes/floor", "2");
        return TimeseriesDataPoint.of(
                thingId, path, timestamp, JsonValue.of(value), 1L, tags, "cel");
    }

    /** Drains a publisher synchronously — used only for the AfterClass DB-drop. */
    private static void blockUntilComplete(final org.reactivestreams.Publisher<?> publisher)
            throws Exception {

        final java.util.concurrent.CompletableFuture<Void> future =
                new java.util.concurrent.CompletableFuture<>();
        publisher.subscribe(new Subscriber<Object>() {

            @Override
            public void onSubscribe(final Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(final Object item) { /* discard */ }
            @Override
            public void onError(final Throwable t) { future.completeExceptionally(t); }
            @Override
            public void onComplete() { future.complete(null); }
        });
        future.get();
    }

    @SuppressWarnings("unused")
    private static Document anyDoc() {
        // Suppress import-purge: keeps the Document import explicit so future authors don't
        // have to remember to add it when extending the test.
        return new Document();
    }
}
