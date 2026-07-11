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
package org.eclipse.ditto.timeseries.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.junit.Before;
import org.junit.Test;

/**
 * Conformance + routing tests for {@link TimeseriesQueryPlanner}.
 * <p>
 * The conformance battery runs every query shape through the planner over a scan-only
 * {@link InMemoryTimeseriesAdapter} and asserts exact, hand-computed results — proving the portable
 * (scan + kernel) path is correct end-to-end and matches the values MongoDB produced live for the
 * same data. The routing tests prove the planner delegates to the backend when it can push a
 * bucketed aggregation down, and falls back to {@code scan} otherwise.
 */
public final class TimeseriesQueryPlannerTest {

    private static final ThingId THING = ThingId.of("org.eclipse.ditto.ts", "heat");
    private static final JsonPointer FLOW =
            JsonPointer.of("/features/systemTemperatures/properties/flowTemperature");
    private static final JsonPointer RETURN =
            JsonPointer.of("/features/systemTemperatures/properties/returnTemperature");
    private static final Instant T0 = Instant.parse("2024-06-01T00:00:00Z");
    private static final Instant FROM = T0.minusSeconds(60);
    private static final Instant TO = T0.plusSeconds(120);
    private static final Duration STEP_2S = Duration.ofSeconds(2);

    private InMemoryTimeseriesAdapter adapter;
    private TimeseriesQueryPlanner planner;

    @Before
    public void setUp() {
        adapter = new InMemoryTimeseriesAdapter();
        // buckets (2s): :00=[10], :02=[12,14], gap, :10=[40,44]
        adapter.ingest(THING, FLOW, T0, 10);
        adapter.ingest(THING, FLOW, T0.plusSeconds(2), 12);
        adapter.ingest(THING, FLOW, T0.plusSeconds(3), 14);
        adapter.ingest(THING, FLOW, T0.plusSeconds(10), 40);
        adapter.ingest(THING, FLOW, T0.plusSeconds(11), 44);
        adapter.ingest(THING, RETURN, T0, 30);
        adapter.ingest(THING, RETURN, T0.plusSeconds(2), 34);
        adapter.ingest(THING, RETURN, T0.plusSeconds(10), 60);
        planner = new TimeseriesQueryPlanner(adapter);
    }

    // --- Conformance: portable (scan + kernel) path over a scan-only backend ---

    @Test
    public void rawReadReturnsAllPointsAscending() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                TimeseriesQuery.of(THING, List.of(FLOW), FROM, TO)));

        assertThat(values(data)).containsExactly(10.0, 12.0, 14.0, 40.0, 44.0);
    }

    @Test
    public void avgWithStepBucketsInKernel() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                query(STEP_2S, Aggregation.AVG, null, null, FLOW)));

        assertThat(values(data)).containsExactly(10.0, 13.0, 42.0); // :00, :02 (avg 12,14), :10 (avg 40,44)
    }

    @Test
    public void fillLinearInterpolatesGapsInKernel() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                query(STEP_2S, Aggregation.AVG, FillStrategy.LINEAR, null, FLOW)));

        assertThat(data).hasSize(6);
        assertThat(value(data.get(2))).isCloseTo(20.25, within(1e-9)); // :04
        assertThat(value(data.get(3))).isCloseTo(27.5, within(1e-9));  // :06
        assertThat(value(data.get(4))).isCloseTo(34.75, within(1e-9)); // :08
        assertThat(data.get(0).isGap()).isFalse();
        assertThat(data.get(2).isGap()).isTrue();
        assertThat(data.get(5).isGap()).isFalse();
    }

    @Test
    public void percentilePerBucketInKernel() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                queryWithPercentile(STEP_2S, 95.0, FLOW)));

        assertThat(value(data.get(0))).isCloseTo(10.0, within(1e-9)); // [10]
        assertThat(value(data.get(1))).isCloseTo(13.9, within(1e-9)); // [12,14] p95
        assertThat(value(data.get(2))).isCloseTo(43.8, within(1e-9)); // [40,44] p95
    }

    @Test
    public void derivativeWholeSeriesInKernel() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                query(null, Aggregation.DERIVATIVE, null, null, FLOW)));

        assertThat(data).hasSize(4);
        assertThat(value(data.get(0))).isCloseTo(1.0, within(1e-9));               // (12-10)/2s
        assertThat(value(data.get(1))).isCloseTo(2.0, within(1e-9));               // (14-12)/1s
        assertThat(value(data.get(2))).isCloseTo(26.0 / 7.0, within(1e-9));        // (40-14)/7s
        assertThat(value(data.get(3))).isCloseTo(4.0, within(1e-9));               // (44-40)/1s
    }

    @Test
    public void integralWholeSeriesInKernel() {
        final List<TimeseriesDataValue> data = single(planner.execute(
                query(null, Aggregation.INTEGRAL, null, null, FLOW)));

        // trapezoids: 22 + 13 + 189 + 42 = 266, stamped at the last point
        assertThat(data).hasSize(1);
        assertThat(value(data.get(0))).isCloseTo(266.0, within(1e-9));
        assertThat(data.get(0).getTimestamp()).isEqualTo(T0.plusSeconds(11));
    }

    @Test
    public void multiPropertyReturnsOneResultPerPathInOrder() {
        final List<TimeseriesQueryResult> results = join(planner.execute(
                query(STEP_2S, Aggregation.AVG, null, null, FLOW, RETURN)));

        assertThat(results).hasSize(2);
        // JsonPointer is both Iterable and CharSequence -> cast to disambiguate assertThat.
        assertThat((Object) results.get(0).getPath()).isEqualTo(FLOW);
        assertThat(values(results.get(0).getData())).containsExactly(10.0, 13.0, 42.0);
        assertThat((Object) results.get(1).getPath()).isEqualTo(RETURN);
        assertThat(values(results.get(1).getData())).containsExactly(30.0, 34.0, 60.0);
    }

    // --- Routing: native delegation vs portable (scan + kernel) ---

    @Test
    public void nativeQueryAdapterIsDelegatedToNotScanned() {
        final boolean[] scanned = {false};
        final TimeseriesQueryResult sentinel = TimeseriesQueryResult.of(THING, FLOW,
                TimeseriesQuery.of(THING, List.of(FLOW), FROM, TO),
                TimeseriesResultMeta.of(0, null, "null"), List.of());
        final TimeseriesQueryPlanner nativePlanner = new TimeseriesQueryPlanner(
                capabilityAdapter(true, scanned, List.of(sentinel)));

        // A complete-native backend: the planner delegates the whole query to it, transparently.
        final List<TimeseriesQueryResult> results = join(nativePlanner.execute(
                query(STEP_2S, Aggregation.AVG, null, null, FLOW)));

        assertThat(results).containsExactly(sentinel); // came from the backend's native query(...)
        assertThat(scanned[0]).isFalse();              // native path: scan was not used
    }

    @Test
    public void scanOnlyAdapterUsesScanAndKernel() {
        final boolean[] scanned = {false};
        final TimeseriesQueryPlanner portablePlanner = new TimeseriesQueryPlanner(
                capabilityAdapter(false, scanned, List.of()));

        // A scan-only backend: the planner must fetch raw points via scan and use the kernel.
        join(portablePlanner.execute(query(STEP_2S, Aggregation.AVG, null, null, FLOW)));

        assertThat(scanned[0]).isTrue();
    }

    // --- helpers ---

    private static TimeseriesQuery query(final Duration step, final Aggregation aggregation,
            final FillStrategy fill, final ZoneId tz, final JsonPointer... paths) {
        return TimeseriesQuery.of(THING, List.of(paths), FROM, TO, step, aggregation, fill, null, tz);
    }

    private static TimeseriesQuery queryWithPercentile(final Duration step, final double percentile,
            final JsonPointer... paths) {
        return TimeseriesQuery.of(THING, List.of(paths), FROM, TO, step, Aggregation.PERCENTILE,
                null, null, null, percentile);
    }

    private static List<TimeseriesQueryResult> join(
            final CompletionStage<List<TimeseriesQueryResult>> stage) {
        return stage.toCompletableFuture().join();
    }

    private static List<TimeseriesDataValue> single(
            final CompletionStage<List<TimeseriesQueryResult>> stage) {
        final List<TimeseriesQueryResult> results = join(stage);
        assertThat(results).hasSize(1);
        return results.get(0).getData();
    }

    private static List<Double> values(final List<TimeseriesDataValue> data) {
        return data.stream().map(TimeseriesQueryPlannerTest::value).toList();
    }

    private static double value(final TimeseriesDataValue dataValue) {
        return dataValue.getValue().orElseThrow().asDouble();
    }

    /**
     * An adapter that declares {@code supportsNativeQuery = nativeQuery}, returns {@code nativeResult}
     * from {@code query(...)}, and records whether {@code scan(...)} was invoked.
     */
    private static TimeseriesAdapter capabilityAdapter(final boolean nativeQuery,
            final boolean[] scanned, final List<TimeseriesQueryResult> nativeResult) {

        return new TimeseriesAdapter() {
            @Override
            public Capabilities capabilities() {
                return Capabilities.builder().supportsNativeQuery(nativeQuery).build();
            }

            @Override
            public CompletionStage<Void> initialize(final TimeseriesAdapterConfig config) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> shutdown() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public HealthStatus getHealth() {
                return HealthStatus.UP;
            }

            @Override
            public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
                return CompletableFuture.completedFuture(nativeResult);
            }

            @Override
            public CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId,
                    final JsonPointer path, final Instant from, final Instant to, final int limit) {
                scanned[0] = true;
                return CompletableFuture.completedFuture(List.of());
            }
        };
    }
}
