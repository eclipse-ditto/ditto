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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.junit.Test;

/**
 * Conformance suite for the {@link TimeseriesAdapter} SPI: the <b>push-down</b> and <b>portable</b>
 * execution paths must return identical results for the same data and query.
 * <p>
 * {@link TimeseriesComputeKernel} is documented as "the reference answer any native push-down
 * implementation is expected to match". {@link TimeseriesQueryPlannerTest} covers which path the
 * planner <em>chooses</em>; this suite covers whether the two paths <em>agree</em>. Without it a
 * backend could quietly disagree with the kernel — on sample-vs-population standard deviation, on
 * how an empty bucket is represented, or on the ordering that {@code first}/{@code last} depend on —
 * and every routing test would still pass.
 * <p>
 * Both sides run through {@link TimeseriesQueryPlanner}, so this exercises the real dispatch:
 * <ul>
 *   <li><b>portable</b> — {@link InMemoryTimeseriesAdapter} declares
 *   {@linkplain Capabilities#minimal() minimal} capabilities, so the planner scans and computes in
 *   the kernel;</li>
 *   <li><b>push-down</b> — {@link PushDownAdapter} declares
 *   {@linkplain Capabilities#nativeQuery() a complete native query} and reduces the buckets itself,
 *   standing in for a backend such as MongoDB.</li>
 * </ul>
 * The push-down side deliberately reduces each bucket with plain loops rather than calling the
 * kernel's reductions, so the aggregation semantics are genuinely compared rather than trivially
 * shared. It does align buckets via the public {@link TimeseriesComputeKernel#bucketStart} contract,
 * because grid alignment is a published part of the SPI that a real backend targets too (MongoDB
 * reaches the same grid through {@code $dateTrunc}).
 */
public final class TimeseriesBackendConformanceTest {

    private static final ThingId THING = ThingId.of("org.eclipse.ditto.ts", "heat");
    private static final JsonPointer FLOW =
            JsonPointer.of("/features/circuit/properties/flowTemperature");
    private static final Instant T0 = Instant.parse("2024-06-01T00:00:00Z");
    private static final Instant FROM = T0.minusSeconds(60);
    private static final Instant TO = T0.plusSeconds(120);
    private static final Duration STEP = Duration.ofSeconds(2);

    /**
     * Buckets at a 2 s step: {@code :00=[10]}, {@code :02=[12,14,11]}, gap, {@code :10=[44,40]}.
     * Deliberately shaped so the reductions are distinguishable — the {@code :02} bucket has three
     * unordered values so {@code first}/{@code last}/{@code stddev} cannot accidentally agree, and
     * {@code :10} is descending so {@code first} is not also {@code max}.
     */
    private static void seed(final BiConsumerLike sink) {
        sink.accept(T0, 10);
        sink.accept(T0.plusSeconds(2), 12);
        sink.accept(T0.plusSeconds(3), 14);
        sink.accept(T0.plusSeconds(3).plusMillis(500), 11);
        sink.accept(T0.plusSeconds(10), 44);
        sink.accept(T0.plusSeconds(11), 40);
    }

    @FunctionalInterface
    private interface BiConsumerLike {
        void accept(Instant timestamp, double value);
    }

    @Test
    public void avgAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.AVG);
    }

    @Test
    public void minAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.MIN);
    }

    @Test
    public void maxAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.MAX);
    }

    @Test
    public void sumAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.SUM);
    }

    @Test
    public void countAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.COUNT);
    }

    @Test
    public void firstAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.FIRST);
    }

    @Test
    public void lastAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.LAST);
    }

    @Test
    public void stddevAgreesAcrossBothPaths() {
        assertPathsAgree(Aggregation.STDDEV);
    }

    /**
     * Pins the flavour of standard deviation, because this is the classic silent divergence: the
     * kernel computes the <em>sample</em> standard deviation, so a bucket holding a single point has
     * no defined value. MongoDB's {@code $stdDevSamp} behaves the same way; a backend reaching for
     * {@code $stdDevPop} would return {@code 0} here and disagree.
     */
    @Test
    public void stddevIsSampleNotPopulation() {
        final List<TimeseriesDataValue> portable = portable(Aggregation.STDDEV);

        // Bucket :00 holds exactly one point (10) -> sample stddev undefined.
        assertThat(portable.get(0).getValue()).isEmpty();
        // Bucket :02 holds [12, 14, 11] -> sample stddev = 1.5275252316519465.
        assertThat(portable.get(1).getValue().orElseThrow().asDouble())
                .isCloseTo(1.5275252316519465, within(1e-9));
    }

    /** An empty interior bucket must be absent on both paths when no fill strategy is requested. */
    @Test
    public void emptyBucketsAreOmittedIdenticallyOnBothPaths() {
        final List<TimeseriesDataValue> portable = portable(Aggregation.AVG);
        final List<TimeseriesDataValue> pushDown = pushDown(Aggregation.AVG);

        assertThat(timestamps(portable)).containsExactly(
                T0, T0.plusSeconds(2), T0.plusSeconds(10));
        assertThat(timestamps(pushDown)).isEqualTo(timestamps(portable));
    }

    /**
     * Gap fill is the behaviour most likely to diverge in a real backend — MongoDB has its own
     * {@code $densify}/{@code $fill} rather than the kernel's interpolation — and a divergence is
     * invisible in the values alone, because an interpolated point looks like an observed one. The
     * bucket at {@code :06} is empty in the fixture, so both paths must synthesise it identically
     * <em>and</em> flag it as a gap.
     */
    @Test
    public void linearFillAgreesAcrossBothPaths() {
        final TimeseriesQuery filled = query(Aggregation.AVG, FillStrategy.LINEAR, null, STEP);

        final List<TimeseriesDataValue> portable = portable(filled);
        final List<TimeseriesDataValue> pushDown = pushDown(filled);

        assertThat(timestamps(pushDown)).isEqualTo(timestamps(portable));
        assertThat(values(pushDown)).isEqualTo(values(portable));
        // Non-vacuous: the fixture's gap really was materialised, and flagged.
        assertThat(portable).hasSizeGreaterThan(3);
        assertThat(portable.stream().anyMatch(TimeseriesDataValue::isGap)).isTrue();
    }

    /**
     * Bucket boundaries are anchored to 2000-01-01 <em>in the query's zone</em>, so a zone with a
     * sub-hour offset shifts the whole grid. Asia/Kolkata (+05:30) puts hourly buckets on the half
     * hour; a backend truncating in UTC would land them on the hour and silently disagree.
     */
    @Test
    public void zoneAlignedBucketsAgreeAcrossBothPaths() {
        final ZoneId halfHourOffset = ZoneId.of("Asia/Kolkata");
        final TimeseriesQuery zoned =
                query(Aggregation.AVG, null, halfHourOffset, Duration.ofHours(1));

        final List<TimeseriesDataValue> portable = portable(zoned);
        final List<TimeseriesDataValue> pushDown = pushDown(zoned);

        assertThat(timestamps(pushDown)).isEqualTo(timestamps(portable));
        assertThat(values(pushDown)).isEqualTo(values(portable));
        // Non-vacuous: the grid really is offset from the UTC hour by the zone's :30.
        assertThat(portable).isNotEmpty();
        assertThat(portable.get(0).getTimestamp().atZone(ZoneOffset.UTC).getMinute()).isEqualTo(30);
    }

    private static void assertPathsAgree(final Aggregation aggregation) {
        final List<TimeseriesDataValue> portable = portable(aggregation);
        final List<TimeseriesDataValue> pushDown = pushDown(aggregation);

        assertThat(timestamps(pushDown))
                .as("bucket grid for %s", aggregation.getName())
                .isEqualTo(timestamps(portable));
        assertThat(values(pushDown))
                .as("reduced values for %s", aggregation.getName())
                .isEqualTo(values(portable));
    }

    private static List<TimeseriesDataValue> portable(final Aggregation aggregation) {
        return portable(query(aggregation));
    }

    private static List<TimeseriesDataValue> pushDown(final Aggregation aggregation) {
        return pushDown(query(aggregation));
    }

    private static List<TimeseriesDataValue> portable(final TimeseriesQuery query) {
        final InMemoryTimeseriesAdapter adapter = new InMemoryTimeseriesAdapter();
        seed((t, v) -> adapter.ingest(THING, FLOW, t, v));
        return single(new TimeseriesQueryPlanner(adapter).execute(query));
    }

    private static List<TimeseriesDataValue> pushDown(final TimeseriesQuery query) {
        final PushDownAdapter adapter = new PushDownAdapter();
        seed(adapter::ingest);
        return single(new TimeseriesQueryPlanner(adapter).execute(query));
    }

    private static TimeseriesQuery query(final Aggregation aggregation) {
        return query(aggregation, null, null, STEP);
    }

    private static TimeseriesQuery query(final Aggregation aggregation,
            @Nullable final FillStrategy fill, @Nullable final ZoneId zone, final Duration step) {

        return TimeseriesQuery.of(THING, List.of(FLOW), FROM, TO, step, aggregation,
                fill, null, zone, null);
    }

    private static List<Instant> timestamps(final List<TimeseriesDataValue> data) {
        final List<Instant> out = new ArrayList<>(data.size());
        for (final TimeseriesDataValue value : data) {
            out.add(value.getTimestamp());
        }
        return out;
    }

    /**
     * Renders each point as {@code value|gapFlag} so {@code count} (an int) and the doubles compare
     * structurally, and so a fill divergence cannot hide behind matching values: an interpolated
     * bucket and a genuinely observed one carry the same number but different gap flags.
     */
    private static List<String> values(final List<TimeseriesDataValue> data) {
        final List<String> out = new ArrayList<>(data.size());
        for (final TimeseriesDataValue value : data) {
            out.add(value.getValue().map(JsonValue::toString).orElse("<none>")
                    + "|" + (value.isGap() ? "gap" : "observed"));
        }
        return out;
    }

    private static List<TimeseriesDataValue> single(
            final CompletionStage<List<TimeseriesQueryResult>> stage) {

        final List<TimeseriesQueryResult> results = stage.toCompletableFuture().join();
        assertThat(results).hasSize(1);
        return results.get(0).getData();
    }

    /**
     * A backend that advertises a complete native query and reduces buckets itself — the stand-in
     * for MongoDB. Reductions are plain loops on purpose: sharing the kernel's implementation would
     * make the conformance assertions vacuous.
     */
    private static final class PushDownAdapter implements TimeseriesAdapter {

        private final List<TimeseriesDataValue> points = new ArrayList<>();

        void ingest(final Instant timestamp, final double value) {
            points.add(TimeseriesDataValue.of(timestamp, JsonValue.of(value)));
        }

        @Override
        public Capabilities capabilities() {
            return Capabilities.nativeQuery();
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
            points.add(TimeseriesDataValue.of(dataPoint.getTimestamp(), dataPoint.getValue()));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
            final Duration step = query.getStep().orElseThrow();
            final Aggregation aggregation = query.getAggregation().orElseThrow();
            final ZoneId zone = query.getTimezone().orElse(null);

            final List<TimeseriesDataValue> inRange = new ArrayList<>();
            for (final TimeseriesDataValue point : points) {
                final Instant t = point.getTimestamp();
                if (!t.isBefore(query.getFrom()) && t.isBefore(query.getTo())) {
                    inRange.add(point);
                }
            }
            inRange.sort(Comparator.comparing(TimeseriesDataValue::getTimestamp));

            final Map<Instant, List<Double>> byBucket = new LinkedHashMap<>();
            for (final TimeseriesDataValue point : inRange) {
                byBucket.computeIfAbsent(
                                TimeseriesComputeKernel.bucketStart(point.getTimestamp(), step, zone),
                                k -> new ArrayList<>())
                        .add(point.getValue().orElseThrow().asDouble());
            }

            final List<TimeseriesDataValue> reducedBuckets = new ArrayList<>(byBucket.size());
            for (final Map.Entry<Instant, List<Double>> bucket : byBucket.entrySet()) {
                final JsonValue reduced = reduce(aggregation, bucket.getValue());
                reducedBuckets.add(reduced == null
                        ? TimeseriesDataValue.gap(bucket.getKey(), null)
                        : TimeseriesDataValue.of(bucket.getKey(), reduced));
            }
            final List<TimeseriesDataValue> data =
                    query.getFillStrategy().isPresent() && !reducedBuckets.isEmpty()
                            ? linearFill(reducedBuckets, step)
                            : reducedBuckets;

            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, "number");
            return CompletableFuture.completedFuture(List.of(
                    TimeseriesQueryResult.of(query.getThingId(), query.getPaths().get(0), query,
                            meta, data)));
        }

        /**
         * Materialises the empty buckets between the first and last populated one, interpolating
         * linearly and flagging each as a gap. Hand-rolled for the same reason the reductions are:
         * calling the kernel's own {@code fillBuckets} would make the comparison vacuous.
         * <p>
         * Only the interior is filled — a leading or trailing empty stretch has nothing to
         * interpolate between, so it stays absent.
         */
        private static List<TimeseriesDataValue> linearFill(final List<TimeseriesDataValue> present,
                final Duration step) {

            final List<TimeseriesDataValue> out = new ArrayList<>();
            for (int i = 0; i < present.size(); i++) {
                final TimeseriesDataValue current = present.get(i);
                out.add(current);
                if (i + 1 >= present.size()) {
                    break;
                }
                final TimeseriesDataValue next = present.get(i + 1);
                final double from = current.getValue().orElseThrow().asDouble();
                final double to = next.getValue().orElseThrow().asDouble();

                // How many whole steps separate the two populated buckets?
                long gaps = 0;
                for (Instant t = current.getTimestamp().plus(step);
                        t.isBefore(next.getTimestamp()); t = t.plus(step)) {
                    gaps++;
                }
                final double increment = (to - from) / (gaps + 1);
                Instant t = current.getTimestamp().plus(step);
                for (long g = 1; g <= gaps; g++, t = t.plus(step)) {
                    out.add(TimeseriesDataValue.gap(t, JsonValue.of(from + increment * g)));
                }
            }
            return out;
        }

        /** Deliberately hand-rolled; see the class javadoc. Returns {@code null} for "no value". */
        private static JsonValue reduce(final Aggregation aggregation, final List<Double> values) {
            switch (aggregation) {
                case AVG:
                    double total = 0.0;
                    for (final double v : values) {
                        total += v;
                    }
                    return JsonValue.of(total / values.size());
                case MIN:
                    double min = values.get(0);
                    for (final double v : values) {
                        min = Math.min(min, v);
                    }
                    return JsonValue.of(min);
                case MAX:
                    double max = values.get(0);
                    for (final double v : values) {
                        max = Math.max(max, v);
                    }
                    return JsonValue.of(max);
                case SUM:
                    double sum = 0.0;
                    for (final double v : values) {
                        sum += v;
                    }
                    return JsonValue.of(sum);
                case COUNT:
                    return JsonValue.of(values.size());
                case FIRST:
                    return JsonValue.of(values.get(0));
                case LAST:
                    return JsonValue.of(values.get(values.size() - 1));
                case STDDEV:
                    // Sample standard deviation — undefined for a single observation, matching
                    // MongoDB's $stdDevSamp. A population variant would return 0 here instead.
                    if (values.size() < 2) {
                        return null;
                    }
                    double mean = 0.0;
                    for (final double v : values) {
                        mean += v;
                    }
                    mean /= values.size();
                    double sq = 0.0;
                    for (final double v : values) {
                        sq += (v - mean) * (v - mean);
                    }
                    return JsonValue.of(Math.sqrt(sq / (values.size() - 1)));
                default:
                    throw new IllegalArgumentException(
                            "Not a bucket reduction: " + aggregation.getName());
            }
        }

        @Override
        public CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId,
                final JsonPointer path, final Instant from, final Instant to, final int limit) {

            throw new AssertionError("push-down backend must not be scanned by the planner");
        }
    }
}
