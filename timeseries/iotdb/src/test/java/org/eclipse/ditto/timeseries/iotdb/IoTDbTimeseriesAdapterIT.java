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
package org.eclipse.ditto.timeseries.iotdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nullable;

import org.apache.iotdb.isession.ITableSession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.ITableSessionPool;
import org.apache.iotdb.session.TableSessionBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.SortOrder;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Integration test for {@link IoTDbTimeseriesAdapter} against a real IoTDB 2.x.
 * <p>
 * Skipped unless {@value HOST_ENV} points at a running IoTDB (port from {@value PORT_ENV}, default 6667; user
 * and password {@code root}). Each run uses a throwaway database that is dropped afterwards.
 */
public final class IoTDbTimeseriesAdapterIT {

    private static final String HOST_ENV = "TIMESERIES_IOTDB_TEST_HOST";
    private static final String PORT_ENV = "TIMESERIES_IOTDB_TEST_PORT";
    private static final String DATABASE = "ditto_ts_it_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String NAMESPACE = "it.timeseries.iotdb";
    private static final JsonPointer PATH = JsonPointer.of("/features/environment/properties/temperature");
    private static final JsonPointer HUMIDITY = JsonPointer.of("/features/environment/properties/humidity");
    private static final Instant HOUR = Instant.parse("2026-01-14T10:00:00Z");
    private static final Instant HOUR_END = Instant.parse("2026-01-14T11:00:00Z");

    private static String host;
    private static int port;

    private IoTDbTimeseriesAdapter adapter;
    private ThingId thingId;

    @BeforeClass
    public static void requireIoTDb() {
        host = System.getenv(HOST_ENV);
        Assume.assumeTrue(HOST_ENV + " is not set; skipping the IoTDB integration test.",
                host != null && !host.isEmpty());
        final String rawPort = System.getenv(PORT_ENV);
        port = rawPort == null || rawPort.isEmpty() ? 6667 : Integer.parseInt(rawPort);
    }

    @AfterClass
    public static void dropTestDatabase() throws Exception {
        if (host == null || host.isEmpty()) {
            return;
        }
        try (ITableSession session = rawSession()) {
            session.executeNonQueryStatement("DROP DATABASE IF EXISTS " + DATABASE);
        }
    }

    @Before
    public void setUp() throws Exception {
        adapter = new IoTDbTimeseriesAdapter(config(""));
        adapter.initialize().toCompletableFuture().get();
        thingId = ThingId.of(NAMESPACE, "sensor-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @After
    public void tearDown() throws Exception {
        if (adapter != null) {
            adapter.shutdown().toCompletableFuture().get();
        }
    }

    private static DefaultIoTDbTimeseriesAdapterConfig config(final String extra) {
        return DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.parseString(
                "host = \"" + host + "\"\nport = " + port + "\ndatabase = " + DATABASE + "\n" +
                        "max-query-result-size = 1000\nquery-timeout = 20s\n" + extra));
    }

    private static ITableSession rawSession() throws Exception {
        return new TableSessionBuilder()
                .nodeUrls(List.of(host + ":" + port))
                .username("root")
                .password("root")
                .build();
    }

    @Test
    public void healthIsUpAfterInitialiseAndDownAfterShutdown() throws Exception {
        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.UP);
        assertThat(adapter.checkHealth().toCompletableFuture().get()).isEqualTo(HealthStatus.UP);

        adapter.shutdown().toCompletableFuture().get();

        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(adapter.checkHealth().toCompletableFuture().get()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void initializingAgainClosesThePreviousConnection() throws Exception {
        final ITableSessionPool previousPool = mock(ITableSessionPool.class);
        final IoTDbTimeseriesAdapter reinitialized = IoTDbTimeseriesAdapter.forTesting(previousPool, config(""));
        try {
            reinitialized.initialize().toCompletableFuture().get();

            verify(previousPool).close();
            assertThat(reinitialized.getHealth()).isEqualTo(HealthStatus.UP);
        } finally {
            reinitialized.shutdown().toCompletableFuture().get();
        }
    }

    @Test
    public void initialiseFailsAgainstAnUnreachableNode() {
        final IoTDbTimeseriesAdapter unreachable = new IoTDbTimeseriesAdapter(DefaultIoTDbTimeseriesAdapterConfig.of(
                ConfigFactory.parseString("host = \"127.0.0.1\"\nport = 1\nquery-timeout = 5s")));

        assertThatThrownBy(() -> unreachable.initialize().toCompletableFuture().get())
                .isInstanceOf(ExecutionException.class);
        assertThat(unreachable.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void writeThenQueryReturnsTheStoredDataPoint() throws Exception {
        final TimeseriesDataPoint dp = point(HOUR.plusMillis(123), JsonValue.of(22.5));
        adapter.write(dp).toCompletableFuture().get();

        final TimeseriesQueryResult result = query(rawQuery(HOUR, HOUR_END));

        assertThat(result.getData()).containsExactly(TimeseriesDataValue.of(dp.getTimestamp(), JsonValue.of(22.5)));
        assertThat(result.getMeta().getUnit()).contains("cel");
        assertThat(result.getMeta().getDataType()).isEqualTo("number");
    }

    @Test
    public void stringBooleanIntegerAndNullValuesRoundTrip() throws Exception {
        adapter.writeBatch(List.of(
                point(HOUR, JsonValue.of("on")),
                point(HOUR.plusSeconds(1), JsonValue.of(true)),
                point(HOUR.plusSeconds(2), JsonValue.of(42)),
                point(HOUR.plusSeconds(3), JsonValue.nullLiteral()))).toCompletableFuture().get();

        final List<TimeseriesDataValue> data = query(rawQuery(HOUR, HOUR_END)).getData();

        assertThat(data).extracting(value -> value.getValue().orElseThrow())
                .containsExactly(JsonValue.of("on"), JsonValue.of(true), JsonValue.of(42), JsonValue.nullLiteral());
    }

    @Test
    public void queryOfANamespaceWithoutTableReturnsEmptyData() throws Exception {
        final ThingId unknown = ThingId.of("it.never.written", "thing");
        final TimeseriesQueryResult result =
                adapter.query(TimeseriesQuery.of(unknown, List.of(PATH), HOUR, HOUR_END)).toCompletableFuture()
                        .get().get(0);

        assertThat(result.getData()).isEmpty();
        assertThat(adapter.scan(unknown, PATH, HOUR, HOUR_END, 10).toCompletableFuture().get()).isEmpty();
    }

    @Test
    public void queryAppliesTimeRangeAndLimit() throws Exception {
        writeMinuteSeries(10);

        assertThat(query(rawQuery(HOUR.plus(2, ChronoUnit.MINUTES), HOUR.plus(5, ChronoUnit.MINUTES))).getData())
                .extracting(value -> value.getValue().orElseThrow().asDouble())
                .containsExactly(22.0, 23.0, 24.0);
        final TimeseriesQueryResult limited = query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END,
                null, null, null, 3, null));
        assertThat(limited.getData()).hasSize(3);
        assertThat(limited.getMeta().getHasMore()).contains(true);
    }

    @Test
    public void paginationWalksTheRangeInBothDirections() throws Exception {
        writeMinuteSeries(10);

        assertThat(paginate(SortOrder.ASC)).containsExactly(20.0, 21.0, 22.0, 23.0, 24.0, 25.0, 26.0, 27.0,
                28.0, 29.0);
        assertThat(paginate(SortOrder.DESC)).containsExactly(29.0, 28.0, 27.0, 26.0, 25.0, 24.0, 23.0, 22.0,
                21.0, 20.0);
    }

    @Test
    public void sameTimestampKeepsEveryRevisionInRevisionOrder() throws Exception {
        writeRevisions(HOUR, 1L, 3L, 2L);
        writeRevisions(HOUR.plusSeconds(1), 4L);

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0, 3.0, 4.0);
        assertThat(values(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, null, null, null, null, null,
                null, null, SortOrder.DESC))).containsExactly(4.0, 3.0, 2.0, 1.0);
        assertThat(singleValue(Aggregation.COUNT)).isEqualTo(4.0);
        assertThat(adapter.scan(thingId, PATH, HOUR, HOUR_END, 0).toCompletableFuture().get())
                .extracting(value -> value.getValue().orElseThrow().asDouble())
                .containsExactly(1.0, 2.0, 3.0, 4.0);
    }

    @Test
    public void revisionsAreKeptAndRetriesWrittenOnceAlsoAfterARestart() throws Exception {
        writeRevisions(HOUR, 1L, 2L);
        writeRevisions(HOUR, 2L);
        adapter.shutdown().toCompletableFuture().get();
        adapter = new IoTDbTimeseriesAdapter(config(""));
        adapter.initialize().toCompletableFuture().get();
        writeRevisions(HOUR, 3L, 2L);

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    public void revisionWrittenOnAnotherNodeIsNotOverwritten() throws Exception {
        writeRevisions(HOUR, 1L);
        final IoTDbTimeseriesAdapter otherNode = new IoTDbTimeseriesAdapter(config(""));
        otherNode.initialize().toCompletableFuture().get();
        try {
            otherNode.writeBatch(List.of(point(HOUR.plusSeconds(5), JsonValue.of(2.0), PATH, 2L, Map.of())))
                    .toCompletableFuture().get();
        } finally {
            otherNode.shutdown().toCompletableFuture().get();
        }
        writeRevisions(HOUR.plusSeconds(5), 3L);

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    public void parallelBatchesOfOneTimestampKeepEveryRevision() throws Exception {
        final List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (long revision = 1; revision <= 8; revision++) {
            writes.add(adapter.writeBatch(List.of(point(HOUR, JsonValue.of((double) revision), PATH, revision,
                    Map.of()))).toCompletableFuture());
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get();

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0);
    }

    @Test
    public void revisionsOfOneTimestampInOneBatchAreAllKept() throws Exception {
        adapter.writeBatch(List.of(point(HOUR, JsonValue.of(2.0), PATH, 2L, Map.of()),
                point(HOUR, JsonValue.of(1.0), PATH, 1L, Map.of()))).toCompletableFuture().get();

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0);
    }

    @Test
    public void pageOfOneReadsTheWholeTimestampBeforeCutting() throws Exception {
        writeRevisions(HOUR, 12L, 9L, 10L, 11L);

        assertThat(paginate(SortOrder.ASC, 1, 4)).containsExactly(9.0, 10.0, 11.0, 12.0);
        assertThat(paginate(SortOrder.DESC, 1, 4)).containsExactly(12.0, 11.0, 10.0, 9.0);
    }

    @Test
    public void paginationSplitsOneTimestampByRevision() throws Exception {
        writeRevisions(HOUR, 1L, 2L, 3L);
        writeRevisions(HOUR.plusSeconds(1), 4L, 5L);

        assertThat(paginate(SortOrder.ASC, 2, 3)).containsExactly(1.0, 2.0, 3.0, 4.0, 5.0);
        assertThat(paginate(SortOrder.DESC, 2, 3)).containsExactly(5.0, 4.0, 3.0, 2.0, 1.0);
    }

    @Test
    public void tableWithoutRevisionSlotIsMigrated() throws Exception {
        final String namespace = "it.timeseries.legacy" + UUID.randomUUID().toString().substring(0, 4);
        try (ITableSession session = rawSession()) {
            session.executeNonQueryStatement("USE " + DATABASE);
            session.executeNonQueryStatement("CREATE TABLE " + IoTDbSql.quote(IoTDbSql.tableName("ts_", namespace)) +
                    " (\"thing_id\" STRING TAG, \"path\" STRING TAG, \"value_double\" DOUBLE FIELD, " +
                    "\"value_text\" STRING FIELD, \"value_bool\" BOOLEAN FIELD, \"revision\" INT64 FIELD, " +
                    "\"unit\" STRING FIELD)");
            session.executeNonQueryStatement("INSERT INTO " +
                    IoTDbSql.quote(IoTDbSql.tableName("ts_", namespace)) + " (time, \"thing_id\", \"path\", " +
                    "\"value_double\", \"revision\") VALUES (" + HOUR.toEpochMilli() + ", '" + namespace +
                    ":legacy', '" + PATH + "', 1.0, 1)");
        }
        thingId = ThingId.of(namespace, "legacy");
        writeRevisions(HOUR, 1L, 2L);

        assertThat(values(rawQuery(HOUR, HOUR_END))).containsExactly(1.0, 2.0);
    }

    @Test
    public void queryReturnsOneResultPerPath() throws Exception {
        adapter.writeBatch(List.of(point(HOUR, JsonValue.of(22.5)), point(HOUR, JsonValue.of(65.2), HUMIDITY,
                1L, Map.of()))).toCompletableFuture().get();

        final List<TimeseriesQueryResult> results = adapter.query(
                TimeseriesQuery.of(thingId, List.of(PATH, HUMIDITY), HOUR, HOUR_END)).toCompletableFuture().get();

        assertThat(results).extracting(TimeseriesQueryResult::getPath).containsExactly(PATH, HUMIDITY);
        assertThat(results.get(0).getData().get(0).getValue()).contains(JsonValue.of(22.5));
        assertThat(results.get(1).getData().get(0).getValue()).contains(JsonValue.of(65.2));
    }

    @Test
    public void tagsAreReturnedInResultMeta() throws Exception {
        adapter.write(point(HOUR, JsonValue.of(22.5), PATH, 1L, Map.of("attributes/building", JsonValue.of("A"),
                "attributes/floor", JsonValue.of(2), "attributes/Heated", JsonValue.of(true))))
                .toCompletableFuture().get();

        assertThat(query(rawQuery(HOUR, HOUR_END)).getMeta().getTags())
                .containsEntry("attributes/building", JsonValue.of("A"))
                .containsEntry("attributes/floor", JsonValue.of(2))
                .containsEntry("attributes/Heated", JsonValue.of(true));
    }

    @Test
    public void numericTagFilterMatchesNumberAndStringTagsButStringFilterOnlyStrings() throws Exception {
        adapter.writeBatch(List.of(
                point(HOUR, JsonValue.of(1.0), PATH, 1L, Map.of("floor", JsonValue.of(2))),
                point(HOUR.plusSeconds(1), JsonValue.of(2.0), PATH, 1L, Map.of("floor", JsonValue.of("2"))),
                point(HOUR.plusSeconds(2), JsonValue.of(3.0), PATH, 1L, Map.of("floor", JsonValue.of(3)))))
                .toCompletableFuture().get();
        final TimeseriesQuery query = rawQuery(HOUR, HOUR_END);

        assertThat(query(query.withTagFilters(Map.of("floor", JsonValue.of(2)))).getData()).hasSize(2);
        assertThat(query(query.withTagFilters(Map.of("floor", JsonValue.of("2")))).getData())
                .containsExactly(TimeseriesDataValue.of(HOUR.plusSeconds(1), JsonValue.of(2)));
        assertThat(query(query.withTagFilters(Map.of("/floor", JsonValue.of(3)))).getData()).hasSize(1);
        assertThat(query(query.withTagFilters(Map.of("unknown", JsonValue.of(3)))).getData()).isEmpty();
    }

    @Test
    public void userTextIsNeverInterpretedAsSql() throws Exception {
        final JsonPointer trickyPath = JsonPointer.of("/features/it's/properties/x\" OR 1=1 --");
        final String trickyKey = "attributes/O'Brien\"; DROP DATABASE " + DATABASE;
        adapter.writeBatch(List.of(
                point(HOUR, JsonValue.of(1.0), trickyPath, 1L, Map.of(trickyKey, JsonValue.of("x' OR '1'='1"))),
                point(HOUR, JsonValue.of(2.0), PATH, 1L, Map.of(trickyKey, JsonValue.of("other")))))
                .toCompletableFuture().get();

        final TimeseriesQueryResult result = adapter.query(TimeseriesQuery.of(thingId, List.of(trickyPath), HOUR,
                        HOUR_END).withTagFilters(Map.of(trickyKey, JsonValue.of("x' OR '1'='1"))))
                .toCompletableFuture().get().get(0);

        assertThat(result.getData()).containsExactly(TimeseriesDataValue.of(HOUR, JsonValue.of(1)));
        assertThat(result.getMeta().getTags()).containsEntry(trickyKey, JsonValue.of("x' OR '1'='1"));
        assertThat(query(rawQuery(HOUR, HOUR_END)).getData()).hasSize(1);
    }

    @Test
    public void bucketAggregationsRunPerStep() throws Exception {
        writeRampSeries();

        assertThat(aggregate(Duration.ofHours(1), Aggregation.AVG))
                .containsExactly(TimeseriesDataValue.of(HOUR, JsonValue.of(35.0)));
        assertThat(singleValue(Aggregation.MIN)).isEqualTo(10.0);
        assertThat(singleValue(Aggregation.MAX)).isEqualTo(60.0);
        assertThat(singleValue(Aggregation.SUM)).isEqualTo(210.0);
        assertThat(singleValue(Aggregation.COUNT)).isEqualTo(6.0);
        assertThat(singleValue(Aggregation.FIRST)).isEqualTo(10.0);
        assertThat(singleValue(Aggregation.LAST)).isEqualTo(60.0);
        assertThat(singleValue(Aggregation.STDDEV)).isCloseTo(18.708286, within(1e-5));
        assertThat(aggregate(Duration.ofMinutes(20), Aggregation.COUNT))
                .extracting(value -> value.getValue().orElseThrow().asInt())
                .containsExactly(2, 2, 2);
    }

    @Test
    public void bucketsAlignWithTheKernelGrid() throws Exception {
        final Instant t = Instant.parse("2026-01-14T07:13:00Z");
        adapter.write(point(t, JsonValue.of(1.0))).toCompletableFuture().get();
        final Duration step = Duration.ofHours(5);

        final List<TimeseriesDataValue> data = query(TimeseriesQuery.of(thingId, List.of(PATH),
                t.minus(1, ChronoUnit.DAYS), t.plus(1, ChronoUnit.DAYS), step, Aggregation.COUNT, null, null,
                null)).getData();

        assertThat(data).extracting(TimeseriesDataValue::getTimestamp)
                .containsExactly(TimeseriesComputeKernel.bucketStart(t, step, null));
    }

    @Test
    public void derivativeAndRateAreComputedInIoTDb() throws Exception {
        final Instant t = HOUR;
        adapter.writeBatch(List.of(
                point(t, JsonValue.of(10.0)),
                point(t.plusSeconds(10), JsonValue.of(30.0)),
                point(t.plusSeconds(15), JsonValue.of("skipped")),
                point(t.plusSeconds(20), JsonValue.of(5.0)))).toCompletableFuture().get();

        assertThat(aggregateNoStep(Aggregation.DERIVATIVE)).containsExactly(
                TimeseriesDataValue.of(t.plusSeconds(10), JsonValue.of(2.0)),
                TimeseriesDataValue.of(t.plusSeconds(20), JsonValue.of(-2.5)));
        assertThat(aggregateNoStep(Aggregation.RATE)).containsExactly(
                TimeseriesDataValue.of(t.plusSeconds(10), JsonValue.of(2.0)),
                TimeseriesDataValue.of(t.plusSeconds(20), JsonValue.of(0.5)));
    }

    @Test
    public void steppedDerivativeDifferencesTheLastValuePerBucket() throws Exception {
        writeRampSeries();

        assertThat(aggregate(Duration.ofMinutes(20), Aggregation.DERIVATIVE))
                .extracting(value -> value.getValue().orElseThrow().asDouble())
                .containsExactly(20.0 / 1200, 20.0 / 1200);
    }

    @Test
    public void defaultResultCeilingStaysWithinIoTDbMemoryEstimates() throws Exception {
        adapter.shutdown().toCompletableFuture().get();
        adapter = new IoTDbTimeseriesAdapter(DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.parseString(
                "host = \"" + host + "\"\nport = " + port + "\ndatabase = " + DATABASE)));
        adapter.initialize()
                .toCompletableFuture().get();
        writeRampSeries();

        assertThat(query(rawQuery(HOUR, HOUR_END)).getData()).hasSize(6);
        assertThat(query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, null, null, null, null, null,
                null, null, SortOrder.DESC)).getData()).hasSize(6);
        assertThat(aggregate(Duration.ofMinutes(10), Aggregation.AVG)).hasSize(6);
        assertThat(aggregateNoStep(Aggregation.DERIVATIVE)).hasSize(5);

        adapter.writeBatch(List.of(point(HOUR_END.minusSeconds(1), JsonValue.of(9.0), PATH, 1L,
                Map.of("floor", JsonValue.of(2))))).toCompletableFuture().get();
        assertThat(query(rawQuery(HOUR, HOUR_END).withTagFilters(Map.of("floor", JsonValue.of(2)))).getData())
                .hasSize(1);
    }

    @Test
    public void unsupportedQueryShapesAreRejected() {
        assertInvalid(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, Duration.ofHours(1),
                Aggregation.AVG, null, null, ZoneId.of("Europe/Berlin")));
        assertInvalid(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, null, Aggregation.PERCENTILE,
                null, null, null, 90.0));
        assertInvalid(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, null, Aggregation.INTEGRAL,
                null, null, null));
    }

    @Test
    public void gapFillFillsOnlyInteriorBucketsAndFlagsThemAsGaps() throws Exception {
        adapter.writeBatch(List.of(point(HOUR, JsonValue.of(10.0)),
                point(HOUR.plus(30, ChronoUnit.MINUTES), JsonValue.of(40.0)))).toCompletableFuture().get();

        assertThat(filled(null)).containsExactly("10.0", "40.0");
        assertThat(filled(FillStrategy.NULL)).containsExactly("10.0", "gap:null", "gap:null", "40.0");
        assertThat(filled(FillStrategy.ZERO)).containsExactly("10.0", "gap:0", "gap:0", "40.0");
        assertThat(filled(FillStrategy.PREVIOUS)).containsExactly("10.0", "gap:10.0", "gap:10.0", "40.0");
        assertThat(filled(FillStrategy.LINEAR)).containsExactly("10.0", "gap:20.0", "gap:30.0", "40.0");
    }

    @Test
    public void gapFillIsRejectedWhenTheFilledGridExceedsTheResultCeiling() throws Exception {
        adapter.shutdown().toCompletableFuture().get();
        adapter = new IoTDbTimeseriesAdapter(config("max-query-result-size = 5"));
        adapter.initialize().toCompletableFuture().get();
        adapter.writeBatch(List.of(point(HOUR, JsonValue.of(1.0)),
                point(HOUR.plus(50, ChronoUnit.MINUTES), JsonValue.of(2.0)))).toCompletableFuture().get();

        assertThat(aggregate(Duration.ofMinutes(10), Aggregation.AVG)).hasSize(2);
        assertInvalid(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, Duration.ofMinutes(10),
                Aggregation.AVG, FillStrategy.LINEAR, null, null));
    }

    @Test
    public void crossThingGapFillIsAppliedPerSeries() throws Exception {
        final ThingId a = ThingId.of(NAMESPACE, "a-" + UUID.randomUUID().toString().substring(0, 6));
        final ThingId b = ThingId.of(NAMESPACE, "b-" + UUID.randomUUID().toString().substring(0, 6));
        adapter.writeBatch(List.of(
                point(a, HOUR, JsonValue.of(10.0), PATH, Map.of()),
                point(a, HOUR.plus(30, ChronoUnit.MINUTES), JsonValue.of(40.0), PATH, Map.of()),
                point(b, HOUR.plus(20, ChronoUnit.MINUTES), JsonValue.of(100.0), PATH, Map.of())))
                .toCompletableFuture().get();
        final CrossThingTimeseriesQuery query = CrossThingTimeseriesQuery.of(NAMESPACE, List.of(PATH), HOUR,
                HOUR_END, Duration.ofMinutes(10), Aggregation.AVG, List.of(GroupBy.thingId()), null, null,
                FillStrategy.LINEAR, null);

        final List<AggregatedTimeseriesResult> results =
                adapter.queryCrossThing(query, Map.of(PATH, List.of(a, b))).toCompletableFuture().get();

        assertThat(seriesOf(results, "thingId", a.toString())).containsExactly(10.0, 20.0, 30.0, 40.0);
        assertThat(seriesOf(results, "thingId", b.toString())).containsExactly(100.0);
    }

    @Test
    public void scanReturnsAscendingPointsUpToTheLimit() throws Exception {
        writeMinuteSeries(5);

        assertThat(adapter.scan(thingId, PATH, HOUR, HOUR_END, 3).toCompletableFuture().get())
                .extracting(value -> value.getValue().orElseThrow().asDouble())
                .containsExactly(20.0, 21.0, 22.0);
    }

    @Test
    public void crossThingAggregatesOnlyPermittedThingsPerPath() throws Exception {
        final ThingId a = ThingId.of(NAMESPACE, "a-" + UUID.randomUUID().toString().substring(0, 6));
        final ThingId b = ThingId.of(NAMESPACE, "b-" + UUID.randomUUID().toString().substring(0, 6));
        final String crossNamespace = NAMESPACE;
        adapter.writeBatch(List.of(
                point(a, HOUR, JsonValue.of(10.0), PATH, Map.of("attributes/building", JsonValue.of("A"))),
                point(a, HOUR.plusSeconds(60), JsonValue.of(20.0), PATH, Map.of("attributes/building", JsonValue.of("A"))),
                point(b, HOUR, JsonValue.of(100.0), PATH, Map.of("attributes/building", JsonValue.of("B"))),
                point(b, HOUR, JsonValue.of(50.0), HUMIDITY, Map.of("attributes/building", JsonValue.of("B")))))
                .toCompletableFuture().get();
        final CrossThingTimeseriesQuery byThing = crossThing(crossNamespace, List.of(PATH, HUMIDITY),
                Aggregation.AVG, List.of(GroupBy.thingId()), null, null);

        final Map<JsonPointer, Collection<ThingId>> permitted = new LinkedHashMap<>();
        permitted.put(PATH, List.of(a, b));
        final List<AggregatedTimeseriesResult> results =
                adapter.queryCrossThing(byThing, permitted).toCompletableFuture().get();

        assertThat(results).extracting(AggregatedTimeseriesResult::getPath).containsOnly(PATH);
        assertThat(seriesOf(results, "thingId", a.toString())).containsExactly(15.0);
        assertThat(seriesOf(results, "thingId", b.toString())).containsExactly(100.0);
        assertThat(adapter.queryCrossThing(byThing, Map.of(PATH, List.of())).toCompletableFuture().get()).isEmpty();
        assertThat(adapter.queryCrossThing(byThing, Map.of()).toCompletableFuture().get()).isEmpty();
    }

    @Test
    public void crossThingFiltersAndGroupsByTags() throws Exception {
        final ThingId a = ThingId.of(NAMESPACE, "a-" + UUID.randomUUID().toString().substring(0, 6));
        final ThingId b = ThingId.of(NAMESPACE, "b-" + UUID.randomUUID().toString().substring(0, 6));
        final ThingId c = ThingId.of(NAMESPACE, "c-" + UUID.randomUUID().toString().substring(0, 6));
        adapter.writeBatch(List.of(
                point(a, HOUR, JsonValue.of(1.0), PATH, Map.of("attributes/building", JsonValue.of("A"),
                        "attributes/floor", JsonValue.of(1))),
                point(b, HOUR, JsonValue.of(3.0), PATH, Map.of("attributes/building", JsonValue.of("A"),
                        "attributes/floor", JsonValue.of(2))),
                point(c, HOUR, JsonValue.of(7.0), PATH, Map.of("attributes/building", JsonValue.of("B"),
                        "attributes/floor", JsonValue.of(2)))))
                .toCompletableFuture().get();
        final Map<JsonPointer, Collection<ThingId>> permitted = Map.of(PATH, List.of(a, b, c));

        final List<AggregatedTimeseriesResult> byBuilding = adapter.queryCrossThing(
                crossThing(NAMESPACE, List.of(PATH), Aggregation.AVG,
                        List.of(GroupBy.tag("attributes/building")), null, null), permitted)
                .toCompletableFuture().get();
        assertThat(seriesOf(byBuilding, "attributes/building", "A")).containsExactly(2.0);
        assertThat(seriesOf(byBuilding, "attributes/building", "B")).containsExactly(7.0);

        final List<AggregatedTimeseriesResult> filtered = adapter.queryCrossThing(
                crossThing(NAMESPACE, List.of(PATH), Aggregation.COUNT, List.of(),
                        "and(eq(attributes/building,'A'),ge(attributes/floor,2))", null), permitted)
                .toCompletableFuture().get();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getData()).extracting(value -> value.getValue().orElseThrow().asInt())
                .containsExactly(1);

        final List<AggregatedTimeseriesResult> negated = adapter.queryCrossThing(
                crossThing(NAMESPACE, List.of(PATH), Aggregation.COUNT, List.of(),
                        "or(ne(attributes/building,'A'),like(attributes/building,'?'),exists(attributes/none))",
                        null), permitted)
                .toCompletableFuture().get();
        assertThat(negated.get(0).getData()).extracting(value -> value.getValue().orElseThrow().asInt())
                .containsExactly(3);
    }

    @Test
    public void crossThingFailsLoudlyAboveMaxGroups() throws Exception {
        final List<TimeseriesDataPoint> points = new ArrayList<>();
        final List<ThingId> things = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final ThingId id = ThingId.of(NAMESPACE, "g" + i + "-" + UUID.randomUUID().toString().substring(0, 6));
            things.add(id);
            points.add(point(id, HOUR, JsonValue.of(i), PATH, Map.of()));
        }
        adapter.writeBatch(points).toCompletableFuture().get();

        assertThatThrownBy(() -> adapter.queryCrossThing(crossThing(NAMESPACE, List.of(PATH), Aggregation.AVG,
                        List.of(GroupBy.thingId()), null, 2), Map.of(PATH, things)).toCompletableFuture().get())
                .hasCauseInstanceOf(TimeseriesQueryInvalidException.class);
        assertThat(adapter.queryCrossThing(crossThing(NAMESPACE, List.of(PATH), Aggregation.AVG,
                List.of(GroupBy.thingId()), null, 3), Map.of(PATH, things)).toCompletableFuture().get()).hasSize(3);
    }

    @Test
    public void discoverContributorsReturnsAtMostLimitPlusOneThings() throws Exception {
        final String namespace = "it.discovery" + UUID.randomUUID().toString().substring(0, 6).replace("-", "");
        final List<TimeseriesDataPoint> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final ThingId id = ThingId.of(namespace, "t" + i);
            points.add(point(id, HOUR, JsonValue.of(i), PATH, Map.of()));
            points.add(point(id, HOUR, JsonValue.of(i), HUMIDITY, Map.of()));
        }
        adapter.writeBatch(points).toCompletableFuture().get();
        final CrossThingTimeseriesQuery query =
                crossThing(namespace, List.of(PATH, HUMIDITY), Aggregation.AVG, List.of(), null, null);

        final Map<JsonPointer, List<ThingId>> all = adapter.discoverContributors(query, 10).toCompletableFuture().get();
        final Map<JsonPointer, List<ThingId>> capped =
                adapter.discoverContributors(query, 2).toCompletableFuture().get();

        assertThat(all.get(PATH)).hasSize(5);
        assertThat(all.get(HUMIDITY)).hasSize(5);
        assertThat(capped.values().stream().flatMap(List::stream).distinct().count()).isEqualTo(3);
    }

    @Test
    public void retentionIsAppliedAsTableTtlAndReconciledOnRestart() throws Exception {
        final String namespace = "it.retention" + UUID.randomUUID().toString().substring(0, 6).replace("-", "");
        final String table = IoTDbSql.tableName("ts_", namespace);
        adapter.shutdown().toCompletableFuture().get();

        adapter = new IoTDbTimeseriesAdapter(config("retention = 7d"));
        adapter.initialize().toCompletableFuture().get();
        adapter.write(point(ThingId.of(namespace, "t"), Instant.now(), JsonValue.of(1), PATH, Map.of()))
                .toCompletableFuture().get();
        assertThat(ttlOf(table)).isEqualTo(String.valueOf(Duration.ofDays(7).toMillis()));
        adapter.shutdown().toCompletableFuture().get();

        adapter = new IoTDbTimeseriesAdapter(config("retention-overrides { \"" + namespace + "\" = 1d }"));
        adapter.initialize().toCompletableFuture().get();
        adapter.write(point(ThingId.of(namespace, "t"), Instant.now(), JsonValue.of(2), PATH, Map.of()))
                .toCompletableFuture().get();
        assertThat(ttlOf(table)).isEqualTo(String.valueOf(Duration.ofDays(1).toMillis()));
    }

    private static String ttlOf(final String table) throws Exception {
        try (ITableSession session = rawSession();
                SessionDataSet dataSet = session.executeQueryStatement(
                        "SHOW TABLES DETAILS FROM " + DATABASE)) {
            final SessionDataSet.DataIterator row = dataSet.iterator();
            while (row.next()) {
                if (table.equals(row.getString("TableName"))) {
                    return row.getString("TTL(ms)");
                }
            }
        }
        throw new AssertionError("Table <" + table + "> not found.");
    }

    private void assertInvalid(final TimeseriesQuery query) {
        assertThatThrownBy(() -> adapter.query(query).toCompletableFuture().get())
                .hasCauseInstanceOf(TimeseriesQueryInvalidException.class);
    }

    private TimeseriesQueryResult query(final TimeseriesQuery query) throws Exception {
        return adapter.query(query).toCompletableFuture().get().get(0);
    }

    private TimeseriesQuery rawQuery(final Instant from, final Instant to) {
        return TimeseriesQuery.of(thingId, List.of(PATH), from, to);
    }

    private List<Double> paginate(final SortOrder order) throws Exception {
        return paginate(order, 3, 4);
    }

    private List<Double> paginate(final SortOrder order, final int pageSize, final int expectedPages)
            throws Exception {

        final List<Double> collected = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            final TimeseriesQueryResult page = query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END,
                    null, null, null, pageSize, null, null, cursor, order));
            page.getData().forEach(value -> collected.add(value.getValue().orElseThrow().asDouble()));
            cursor = page.getMeta().getNextCursor().orElse(null);
            pages++;
            assertThat(pages).as("pagination must terminate").isLessThanOrEqualTo(10);
        } while (cursor != null);
        assertThat(pages).isEqualTo(expectedPages);
        return collected;
    }

    private List<Double> values(final TimeseriesQuery query) throws Exception {
        return query(query).getData().stream().map(value -> value.getValue().orElseThrow().asDouble()).toList();
    }

    /** Writes one batch per revision, each with the revision as its value. */
    private void writeRevisions(final Instant timestamp, final long... revisions) throws Exception {
        for (final long revision : revisions) {
            adapter.writeBatch(List.of(point(timestamp, JsonValue.of((double) revision), PATH, revision, Map.of())))
                    .toCompletableFuture().get();
        }
    }

    private List<TimeseriesDataValue> aggregate(final Duration step, final Aggregation aggregation)
            throws Exception {

        return query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, step, aggregation, null, null,
                null)).getData();
    }

    private List<String> filled(@Nullable final FillStrategy fill) throws Exception {
        return query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, Duration.ofMinutes(10),
                Aggregation.AVG, fill, null, null)).getData().stream()
                .map(value -> (value.isGap() ? "gap:" : "") +
                        value.getValue().map(JsonValue::toString).orElse("null"))
                .toList();
    }

    private List<TimeseriesDataValue> aggregateNoStep(final Aggregation aggregation) throws Exception {
        return query(TimeseriesQuery.of(thingId, List.of(PATH), HOUR, HOUR_END, null, aggregation, null, null,
                null)).getData();
    }

    private double singleValue(final Aggregation aggregation) throws Exception {
        final List<TimeseriesDataValue> data = aggregate(Duration.ofHours(1), aggregation);
        assertThat(data).hasSize(1);
        return data.get(0).getValue().orElseThrow().asDouble();
    }

    private static List<Double> seriesOf(final List<AggregatedTimeseriesResult> results, final String groupKey,
            final String groupValue) {

        return results.stream()
                .filter(result -> JsonValue.of(groupValue).equals(result.getGroup().get(groupKey)))
                .flatMap(result -> result.getData().stream())
                .map(value -> value.getValue().orElseThrow().asDouble())
                .toList();
    }

    private static CrossThingTimeseriesQuery crossThing(final String namespace, final List<JsonPointer> paths,
            final Aggregation aggregation, final List<GroupBy> groupBy, final String filter,
            final Integer maxGroups) {

        return CrossThingTimeseriesQuery.of(namespace, paths, HOUR, HOUR_END, Duration.ofHours(1), aggregation,
                groupBy, filter, null, null, maxGroups);
    }

    private void writeMinuteSeries(final int count) throws Exception {
        final List<TimeseriesDataPoint> batch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            batch.add(point(HOUR.plus(i, ChronoUnit.MINUTES), JsonValue.of(20.0 + i)));
        }
        adapter.writeBatch(batch).toCompletableFuture().get();
    }

    /**
     * Six points in [10:00, 11:00), values 10..60, ten minutes apart.
     */
    private void writeRampSeries() throws Exception {
        final List<TimeseriesDataPoint> batch = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            batch.add(point(HOUR.plus(10L * i, ChronoUnit.MINUTES), JsonValue.of(10.0 * (i + 1))));
        }
        adapter.writeBatch(batch).toCompletableFuture().get();
    }

    private TimeseriesDataPoint point(final Instant timestamp, final JsonValue value) {
        return point(timestamp, value, PATH, 1L, Map.of());
    }

    private TimeseriesDataPoint point(final Instant timestamp, final JsonValue value, final JsonPointer path,
            final long revision, final Map<String, JsonValue> tags) {

        return TimeseriesDataPoint.of(thingId, path, timestamp, value, revision, tags, "cel");
    }

    private static TimeseriesDataPoint point(final ThingId thingId, final Instant timestamp, final JsonValue value,
            final JsonPointer path, final Map<String, JsonValue> tags) {

        return TimeseriesDataPoint.of(thingId, path, timestamp, value, 1L, tags, "cel");
    }
}
