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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.iotdb.isession.ITableSession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.ITableSessionPool;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.tsfile.write.record.Tablet;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.TimeseriesCursor;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link IoTDbTimeseriesAdapter} without a live IoTDB.
 */
public final class IoTDbTimeseriesAdapterTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");
    private static final JsonPointer PATH = JsonPointer.of("/features/env/properties/temperature");
    private static final Instant FROM = Instant.parse("2026-01-14T10:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-14T11:00:00Z");
    private static final String TABLE = "ts_org_2eeclipse_2editto";

    private ITableSessionPool pool;
    private ITableSession session;
    private IoTDbTimeseriesAdapter adapter;

    @Before
    public void setUp() throws Exception {
        pool = mock(ITableSessionPool.class);
        session = mock(ITableSession.class);
        when(pool.getSession()).thenReturn(session);
        adapter = IoTDbTimeseriesAdapter.forTesting(pool, DefaultIoTDbTimeseriesAdapterConfig.of(
                ConfigFactory.parseString("query-timeout = 300ms\nmax-sessions = 2")));
    }

    @After
    public void tearDown() {
        adapter.shutdown().toCompletableFuture().join();
    }

    @Test
    public void advertisesNativeQueryCrossThingAndPushedDownAggregations() {
        assertThat(adapter.capabilities().supportsNativeQuery()).isTrue();
        assertThat(adapter.capabilities().supportsNativeCrossThingQuery()).isTrue();
        assertThat(adapter.capabilities().getPushableAggregations()).containsExactlyInAnyOrder(Aggregation.AVG,
                Aggregation.MIN, Aggregation.MAX, Aggregation.SUM, Aggregation.COUNT, Aggregation.FIRST,
                Aggregation.LAST, Aggregation.STDDEV, Aggregation.DERIVATIVE, Aggregation.RATE);
        assertThat(adapter.capabilities().getNativeFillStrategies()).isEmpty();
    }

    @Test
    public void rejectsNullConfig() {
        assertThatNullPointerException().isThrownBy(() -> new IoTDbTimeseriesAdapter((IoTDbTimeseriesAdapterConfig) null));
    }

    @Test
    public void operationsFailBeforeInitialize() {
        final IoTDbTimeseriesAdapter uninitialised =
                new IoTDbTimeseriesAdapter(DefaultIoTDbTimeseriesAdapterConfig.of(ConfigFactory.empty()));

        assertThat(uninitialised.getHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(uninitialised.checkHealth().toCompletableFuture().join()).isEqualTo(HealthStatus.DOWN);
        assertThatThrownBy(() -> uninitialised.query(TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO))
                .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> uninitialised.write(point(JsonValue.of(1))).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    public void checkHealthIsUpWhenIoTDbAnswersThePing() throws Exception {
        final SessionDataSet dataSet = mock(SessionDataSet.class);
        when(session.executeQueryStatement("SELECT 1")).thenReturn(dataSet);

        assertThat(adapter.checkHealth().toCompletableFuture().join()).isEqualTo(HealthStatus.UP);
        verify(session).close();
    }

    @Test
    public void checkHealthIsDownWhenNoSessionCanBeObtained() throws Exception {
        when(pool.getSession()).thenThrow(new IoTDBConnectionException("unreachable"));

        assertThat(adapter.checkHealth().toCompletableFuture().join()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void checkHealthIsDownWhenThePingHangs() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        when(session.executeQueryStatement("SELECT 1")).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return mock(SessionDataSet.class);
        });

        final long start = System.nanoTime();
        assertThat(adapter.checkHealth().toCompletableFuture().join()).isEqualTo(HealthStatus.DOWN);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        release.countDown();
    }

    @Test
    public void shutdownClosesThePoolAndIsIdempotent() {
        adapter.shutdown().toCompletableFuture().join();
        adapter.shutdown().toCompletableFuture().join();

        verify(pool).close();
        assertThat(adapter.getHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    public void unsupportedQueryShapesAreRejectedWithoutTouchingIoTDb() throws Exception {
        for (final TimeseriesQuery query : List.of(
                TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO, Duration.ofMinutes(1), Aggregation.AVG,
                        FillStrategy.LINEAR, null, null),
                TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO, Duration.ofDays(1), Aggregation.AVG, null,
                        null, ZoneId.of("Europe/Berlin")),
                TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO, null, Aggregation.PERCENTILE, null, null,
                        null, 50.0),
                TimeseriesQuery.of(THING_ID, List.of(PATH), FROM, TO, null, Aggregation.INTEGRAL, null, null,
                        null))) {
            assertThatThrownBy(() -> adapter.query(query).toCompletableFuture().join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(TimeseriesQueryInvalidException.class);
        }
        verify(pool, never()).getSession();
    }

    @Test
    public void crossThingWithoutAnyPermittedThingDoesNotQuery() throws Exception {
        final CrossThingTimeseriesQuery query = crossThing(List.of());

        assertThat(adapter.queryCrossThing(query, Map.of()).toCompletableFuture().join()).isEmpty();
        assertThat(adapter.queryCrossThing(query, Map.of(PATH, List.of())).toCompletableFuture().join())
                .isEmpty();
        verify(pool, never()).getSession();
    }

    @Test
    public void createTableStatementDeclaresTheSchema() {
        assertThat(IoTDbTimeseriesAdapter.createTableStatement(TABLE, "86400000")).isEqualTo(
                "CREATE TABLE IF NOT EXISTS \"" + TABLE + "\" (\"thing_id\" STRING TAG, \"path\" STRING TAG, " +
                        "\"value_double\" DOUBLE FIELD, \"value_text\" STRING FIELD, \"value_bool\" BOOLEAN FIELD, " +
                        "\"revision\" INT64 FIELD, \"unit\" STRING FIELD, \"rev_slot\" STRING TAG) WITH (TTL=86400000)");
    }

    @Test
    public void rawReadStatementPagesByTime() {
        final String where = IoTDbTimeseriesAdapter.seriesWhere(THING_ID, PATH, FROM, TO);
        assertThat(where).isEqualTo("\"thing_id\" = 'org.eclipse.ditto:sensor-1' AND " +
                "\"path\" = '/features/env/properties/temperature' AND time >= 1768384800000 AND " +
                "time < 1768388400000");

        assertThat(IoTDbTimeseriesAdapter.rawReadStatement(TABLE, "w", null, false))
                .isEqualTo("SELECT * FROM \"" + TABLE + "\" WHERE w ORDER BY time ASC");
        assertThat(IoTDbTimeseriesAdapter.rawReadStatement(TABLE, "w", TimeseriesCursor.of(FROM, 7L), true))
                .isEqualTo("SELECT * FROM \"" + TABLE + "\" WHERE w AND time <= 1768384800000 AND " +
                        "(time < 1768384800000 OR \"revision\" < 7) ORDER BY time DESC");
    }

    @Test
    public void userTextInTheSeriesFilterIsQuoted() {
        final JsonPointer path = JsonPointer.of("/features/x'; DROP TABLE t; --/properties/y");

        assertThat(IoTDbTimeseriesAdapter.seriesWhere(THING_ID, path, FROM, TO))
                .contains("\"path\" = '/features/x''; DROP TABLE t; --/properties/y'");
    }

    @Test
    public void bucketAggregationStatementGroupsOnTheSharedGrid() {
        assertThat(IoTDbTimeseriesAdapter.bucketAggregationStatement(TABLE, "w", Duration.ofMinutes(15),
                Aggregation.COUNT)).isEqualTo("SELECT date_bin(900000ms, time, 946684800000) AS b, count(*) AS v, " +
                "first(\"unit\", time) AS u FROM \"" + TABLE + "\" WHERE w GROUP BY 1 ORDER BY 1");
        assertThat(IoTDbTimeseriesAdapter.aggregateExpression(Aggregation.LAST))
                .isEqualTo("last(\"value_double\", time)");
        assertThat(IoTDbTimeseriesAdapter.aggregateExpression(Aggregation.STDDEV))
                .isEqualTo("stddev_samp(\"value_double\")");
    }

    @Test
    public void derivativeStatementUsesLag() {
        assertThat(IoTDbTimeseriesAdapter.derivativeStatement(TABLE, "w", null, false)).isEqualTo(
                "SELECT t, (v - pv) * 1000.0 / dt AS d FROM (SELECT t, v, LAG(v) OVER (ORDER BY t) AS pv, " +
                        "CAST(t AS INT64) - CAST(LAG(t) OVER (ORDER BY t) AS INT64) AS dt FROM (" +
                        "SELECT time AS t, \"value_double\" AS v FROM \"" + TABLE + "\" WHERE w AND " +
                        "\"value_double\" IS NOT NULL)) WHERE dt > 0 ORDER BY t");
        assertThat(IoTDbTimeseriesAdapter.derivativeStatement(TABLE, "w", Duration.ofMinutes(1), true))
                .contains("(CASE WHEN v < pv THEN v ELSE v - pv END) * 1000.0 / dt")
                .contains("SELECT date_bin(60000ms, time, 946684800000) AS t, last(\"value_double\", time) AS v")
                .contains("GROUP BY 1");
    }

    @Test
    public void pathAccessIsRestrictedPerPath() {
        final JsonPointer other = JsonPointer.of("/features/env/properties/humidity");
        final Map<JsonPointer, Collection<ThingId>> readable = new LinkedHashMap<>();
        readable.put(PATH, List.of(THING_ID, ThingId.of("org.eclipse.ditto", "o'neil")));
        readable.put(other, List.of(THING_ID));

        assertThat(IoTDbTimeseriesAdapter.pathAccessPredicate(readable)).isEqualTo(
                "((\"path\" = '" + PATH + "' AND \"thing_id\" IN ('org.eclipse.ditto:sensor-1', " +
                        "'org.eclipse.ditto:o''neil')) OR (\"path\" = '" + other + "' AND \"thing_id\" IN " +
                        "('org.eclipse.ditto:sensor-1')))");
        assertThat(IoTDbTimeseriesAdapter.pathAccessPredicate(Map.of())).isEqualTo(IoTDbTagPredicates.FALSE);
    }

    @Test
    public void crossThingStatementGroupsBySeriesKeyThenBucket() {
        final String building = IoTDbSql.tagColumn("attributes/building", TagType.STRING);
        final CrossThingTimeseriesQuery query = CrossThingTimeseriesQuery.of("org.eclipse.ditto", List.of(PATH),
                FROM, TO, Duration.ofHours(1), Aggregation.AVG,
                List.of(GroupBy.thingId(), GroupBy.tag("attributes/building")),
                "eq(attributes/building,'A')", null, null, null);

        final IoTDbTimeseriesAdapter.CrossThingStatement statement = IoTDbTimeseriesAdapter.crossThingStatement(
                TABLE, query, Map.of(PATH, List.of(THING_ID)), Set.of(building));

        assertThat(statement.keyColumns()).containsExactly("p", "t", "g1_0");
        assertThat(statement.sql()).isEqualTo("SELECT date_bin(3600000ms, time, 946684800000) AS b, " +
                "\"path\" AS p, \"thing_id\" AS t, \"" + building + "\" AS g1_0, avg(\"value_double\") AS v, " +
                "first(\"unit\", time) AS u FROM \"" + TABLE + "\" WHERE (time >= 1768384800000 AND " +
                "time < 1768388400000 AND \"value_double\" IS NOT NULL AND (\"path\" = '" + PATH + "' AND " +
                "\"thing_id\" IN ('org.eclipse.ditto:sensor-1')) AND (\"" + building + "\" IS NOT NULL AND \"" +
                building + "\" = 'A')) GROUP BY 1, 2, 3, 4 ORDER BY 2, 3, 4, 1");
    }

    @Test
    public void discoveryStatementHasNoThingRestriction() {
        final String sql = IoTDbTimeseriesAdapter.discoveryStatement(TABLE, crossThing(List.of()), Set.of(), 11);

        assertThat(sql).isEqualTo("SELECT \"thing_id\" AS t, \"path\" AS p FROM \"" + TABLE + "\" WHERE " +
                "(time >= 1768384800000 AND time < 1768388400000 AND \"value_double\" IS NOT NULL AND " +
                "\"path\" IN ('" + PATH + "')) GROUP BY 1, 2 ORDER BY 1, 2 LIMIT 11");
    }

    @Test
    public void storedRevisionsStatementLooksUpTheBatchTimestampsOfTheThing() {
        final IoTDbTimeseriesAdapter.Row first = IoTDbTimeseriesAdapter.Row.of(
                TimeseriesDataPoint.of(THING_ID, PATH, FROM, JsonValue.of(1), 3L, Map.of(), null));
        final IoTDbTimeseriesAdapter.Row second = IoTDbTimeseriesAdapter.Row.of(
                TimeseriesDataPoint.of(THING_ID, JsonPointer.of("/features/env/properties/humidity"), FROM,
                        JsonValue.of(2), 3L, Map.of(), null));

        assertThat(IoTDbTimeseriesAdapter.storedRevisionsStatement(TABLE, List.of(first, second)))
                .isEqualTo("SELECT \"path\", time, \"revision\", \"rev_slot\" FROM \"" + TABLE + "\" WHERE " +
                        "\"thing_id\" = 'org.eclipse.ditto:sensor-1' AND time IN (1768384800000)");
    }

    @Test
    public void tabletCarriesTypedValuesAndTagColumns() {
        final TimeseriesDataPoint dataPoint = TimeseriesDataPoint.of(THING_ID, PATH, FROM, JsonValue.of("on"), 3L,
                Map.of("attributes/floor", JsonValue.of(2), "attributes/name", JsonValue.of("x")), "cel");
        final IoTDbTimeseriesAdapter.Row row = IoTDbTimeseriesAdapter.Row.of(dataPoint);
        final Map<String, TagType> tagColumns = new TreeMap<>();
        row.tags().forEach((column, typed) -> tagColumns.put(column, typed.type()));

        final Tablet tablet = IoTDbTimeseriesAdapter.toTablet(TABLE, List.of(row), tagColumns);

        assertThat(row.tags()).containsOnlyKeys(IoTDbSql.tagColumn("attributes/floor", TagType.NUMBER),
                IoTDbSql.tagColumn("attributes/name", TagType.STRING));
        assertThat(tablet.getRowSize()).isEqualTo(1);
        assertThat(tablet.getTimestamp(0)).isEqualTo(FROM.toEpochMilli());
        assertThat(tablet.getValue(0, 3).toString()).isEqualTo("on");
        assertThat(tablet.isNull(0, 2)).isTrue();
        assertThat(tablet.getValue(0, 5)).isEqualTo(3L);
        assertThat(tablet.isNull(0, 7)).isTrue();
        assertThat(tablet.getValue(0, 8)).isEqualTo(2.0);
        assertThat(tablet.getValue(0, 9).toString()).isEqualTo("x");
    }

    private static TimeseriesDataPoint point(final JsonValue value) {
        return TimeseriesDataPoint.of(THING_ID, PATH, FROM, value, 1L, Map.of(), null);
    }

    private static CrossThingTimeseriesQuery crossThing(final List<GroupBy> groupBy) {
        return CrossThingTimeseriesQuery.of("org.eclipse.ditto", List.of(PATH), FROM, TO, Duration.ofHours(1),
                Aggregation.AVG, groupBy, null, null, null, null);
    }
}
