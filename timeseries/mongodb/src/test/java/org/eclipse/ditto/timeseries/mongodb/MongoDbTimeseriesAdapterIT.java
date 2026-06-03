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
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
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
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

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

        // Build a MongoDbConfig from a Typesafe Config rooted at "ditto" — same shape as
        // DefaultMongoDbConfig consumes at runtime. Production code reads `ditto.mongodb.uri`
        // and `ditto.mongodb.database`; the IT does the same here.
        final Config rootConfig = ConfigFactory.parseString(String.format(
                "ditto.mongodb.uri = \"%s\"\nditto.mongodb.database = \"%s\"\n", uri, DATABASE));
        final MongoDbConfig mongoDbConfig =
                DefaultMongoDbConfig.of(DefaultScopedConfig.dittoScoped(rootConfig));
        final MongoDbTimeseriesAdapterConfig config = DefaultMongoDbTimeseriesAdapterConfig.of(
                mongoDbConfig, "ts_", Granularity.SECONDS);
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

    // ----- Phase 2: downsampling + aggregation -------------------------------------------------
    // Deterministic fixture: 6 points inside one UTC hour [10:00, 11:00), values 10..60.
    private static final Instant BUCKET_HOUR = Instant.parse("2026-01-14T10:00:00Z");
    private static final Instant BUCKET_HOUR_END = Instant.parse("2026-01-14T11:00:00Z");

    @Test
    public void downsampleAvgReturnsBucketMean() throws Exception {
        writeRampSeries();
        final List<TimeseriesDataValue> data = aggregate(Duration.ofHours(1), Aggregation.AVG);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getValue().orElseThrow().asDouble()).isEqualTo(35.0);
        assertThat(data.get(0).getTimestamp()).isEqualTo(BUCKET_HOUR);
    }

    @Test
    public void downsampleMinMaxSumCount() throws Exception {
        writeRampSeries();
        assertThat(aggregate(Duration.ofHours(1), Aggregation.MIN).get(0).getValue().orElseThrow().asDouble())
                .isEqualTo(10.0);
        assertThat(aggregate(Duration.ofHours(1), Aggregation.MAX).get(0).getValue().orElseThrow().asDouble())
                .isEqualTo(60.0);
        assertThat(aggregate(Duration.ofHours(1), Aggregation.SUM).get(0).getValue().orElseThrow().asDouble())
                .isEqualTo(210.0);
        assertThat(aggregate(Duration.ofHours(1), Aggregation.COUNT).get(0).getValue().orElseThrow().asInt())
                .isEqualTo(6);
    }

    @Test
    public void downsampleStddevIsSampleStandardDeviation() throws Exception {
        writeRampSeries();
        final double stddev = aggregate(Duration.ofHours(1), Aggregation.STDDEV)
                .get(0).getValue().orElseThrow().asDouble();
        assertThat(stddev).isCloseTo(18.708286, within(1e-5));
    }

    @Test
    public void derivativeReturnsPerSecondRateBetweenPoints() throws Exception {
        writeRampSeries();
        // No step: differenced between consecutive raw points; 6 points -> 5 derivatives.
        // (20-10) / 600s = 0.0166667 per second.
        final List<TimeseriesDataValue> data = aggregate(null, Aggregation.DERIVATIVE);
        assertThat(data).hasSize(5);
        assertThat(data.get(0).getValue().orElseThrow().asDouble()).isCloseTo(0.0166667, within(1e-6));
    }

    @Test
    public void rateTreatsCounterDecreaseAsReset() throws Exception {
        // 95000 -> 100000 (5000 over 600s) then reset to 2000 (treated as 2000 over 600s, not negative).
        adapter.writeBatch(Arrays.asList(
                dataPoint(BUCKET_HOUR.plus(0, ChronoUnit.MINUTES), 95000.0),
                dataPoint(BUCKET_HOUR.plus(10, ChronoUnit.MINUTES), 100000.0),
                dataPoint(BUCKET_HOUR.plus(20, ChronoUnit.MINUTES), 2000.0))).toCompletableFuture().get();

        final List<TimeseriesDataValue> data = aggregate(null, Aggregation.RATE);
        assertThat(data).hasSize(2);
        assertThat(data.get(0).getValue().orElseThrow().asDouble()).isCloseTo(5000.0 / 600.0, within(1e-6));
        assertThat(data.get(1).getValue().orElseThrow().asDouble()).isCloseTo(2000.0 / 600.0, within(1e-6));
    }

    @Test
    public void integralReturnsTrapezoidalArea() throws Exception {
        writeRampSeries();
        // Trapezoidal over 6 points: (15+25+35+45+55) * 600 = 105000 value-seconds, single point.
        final List<TimeseriesDataValue> data = aggregate(null, Aggregation.INTEGRAL);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getValue().orElseThrow().asDouble()).isCloseTo(105000.0, within(1e-3));
    }

    @Test
    public void percentileInterpolatesPerBucket() throws Exception {
        writeRampSeries();
        assertThat(percentile(Duration.ofHours(1), 50.0).get(0).getValue().orElseThrow().asDouble())
                .isEqualTo(35.0);
        assertThat(percentile(Duration.ofHours(1), 95.0).get(0).getValue().orElseThrow().asDouble())
                .isEqualTo(57.5);
    }

    @Test
    public void fillPreviousCarriesValueAcrossInteriorGap() throws Exception {
        // Points in bucket1 [10:00,10:20) and bucket3 [10:40,11:00); bucket2 [10:20,10:40) is empty.
        adapter.writeBatch(Arrays.asList(
                dataPoint(BUCKET_HOUR.plus(5, ChronoUnit.MINUTES), 10.0),
                dataPoint(BUCKET_HOUR.plus(15, ChronoUnit.MINUTES), 20.0),
                dataPoint(BUCKET_HOUR.plus(45, ChronoUnit.MINUTES), 50.0),
                dataPoint(BUCKET_HOUR.plus(55, ChronoUnit.MINUTES), 60.0))).toCompletableFuture().get();

        final TimeseriesQuery query = TimeseriesQuery.of(thingId, Collections.singletonList(PATH),
                BUCKET_HOUR, BUCKET_HOUR_END, Duration.ofMinutes(20), Aggregation.AVG,
                FillStrategy.PREVIOUS, null, null);
        final List<TimeseriesDataValue> data =
                adapter.query(query).toCompletableFuture().get().get(0).getData();

        assertThat(data).hasSize(3);
        assertThat(data.get(0).getValue().orElseThrow().asDouble()).isEqualTo(15.0);
        assertThat(data.get(1).isGap()).isTrue();
        assertThat(data.get(1).getValue().orElseThrow().asDouble()).isEqualTo(15.0);
        assertThat(data.get(2).getValue().orElseThrow().asDouble()).isEqualTo(55.0);
    }

    private void writeRampSeries() throws Exception {
        final int[] minutes = {5, 15, 25, 35, 45, 55};
        final double[] values = {10, 20, 30, 40, 50, 60};
        final List<TimeseriesDataPoint> batch = new ArrayList<>();
        for (int i = 0; i < minutes.length; i++) {
            batch.add(dataPoint(BUCKET_HOUR.plus(minutes[i], ChronoUnit.MINUTES), values[i]));
        }
        adapter.writeBatch(batch).toCompletableFuture().get();
    }

    private List<TimeseriesDataValue> aggregate(final Duration step, final Aggregation agg)
            throws Exception {
        final TimeseriesQuery query = TimeseriesQuery.of(thingId, Collections.singletonList(PATH),
                BUCKET_HOUR, BUCKET_HOUR_END, step, agg, null, null, null);
        return adapter.query(query).toCompletableFuture().get().get(0).getData();
    }

    private List<TimeseriesDataValue> percentile(final Duration step, final double pct)
            throws Exception {
        final TimeseriesQuery query = TimeseriesQuery.of(thingId, Collections.singletonList(PATH),
                BUCKET_HOUR, BUCKET_HOUR_END, step, Aggregation.PERCENTILE, null, null, null, pct);
        return adapter.query(query).toCompletableFuture().get().get(0).getData();
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
