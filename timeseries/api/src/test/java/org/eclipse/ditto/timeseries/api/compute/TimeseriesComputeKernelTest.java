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
package org.eclipse.ditto.timeseries.api.compute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel.TimePoint;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.junit.Test;

/**
 * Unit tests for {@link TimeseriesComputeKernel} — the backend-neutral reference computations.
 */
public final class TimeseriesComputeKernelTest {

    private static final Instant T0 = Instant.parse("2024-06-01T00:00:00Z");

    // --- stepUnitFor ---

    @Test
    public void stepUnitForPicksCoarsestExactUnit() {
        assertThat(TimeseriesComputeKernel.stepUnitFor(Duration.ofDays(2)))
                .isEqualTo(new TimeseriesComputeKernel.StepUnit("day", 2));
        assertThat(TimeseriesComputeKernel.stepUnitFor(Duration.ofHours(3)))
                .isEqualTo(new TimeseriesComputeKernel.StepUnit("hour", 3));
        assertThat(TimeseriesComputeKernel.stepUnitFor(Duration.ofMinutes(15)))
                .isEqualTo(new TimeseriesComputeKernel.StepUnit("minute", 15));
        assertThat(TimeseriesComputeKernel.stepUnitFor(Duration.ofSeconds(5)))
                .isEqualTo(new TimeseriesComputeKernel.StepUnit("second", 5));
    }

    @Test
    public void stepUnitForNullThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> TimeseriesComputeKernel.stepUnitFor(null));
    }

    // --- nextBucket ---

    @Test
    public void nextBucketWithoutZoneAddsFixedDuration() {
        assertThat(TimeseriesComputeKernel.nextBucket(T0, Duration.ofMinutes(5), null))
                .isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    public void nextBucketWithZoneStepsBySpringForwardDstDay() {
        // Europe/Berlin springs forward on 2024-03-31 (02:00 CET -> 03:00 CEST): that calendar day
        // is only 23h long. A tz-aware "1 day" step must span 23h, not a fixed 24h.
        final ZoneId berlin = ZoneId.of("Europe/Berlin");
        final Instant dayStart = ZonedDateTime.of(2024, 3, 31, 0, 0, 0, 0, berlin).toInstant();

        final Instant next = TimeseriesComputeKernel.nextBucket(dayStart, Duration.ofDays(1), berlin);

        assertThat(Duration.between(dayStart, next)).isEqualTo(Duration.ofHours(23));
    }

    // --- bucketStart (parity with MongoDB $dateTrunc) ---

    @Test
    public void bucketStartMatchesMongoDateTrunc() {
        // Golden values captured from MongoDB's $dateTrunc on a live server, covering bin origin
        // (binSize>1), sub-hour tz offsets, and DST transitions. If the kernel's in-memory bucketing
        // ever drifts from MongoDB's, a scan-only adapter's results would silently diverge — these
        // pin them together.
        final ZoneId berlin = ZoneId.of("Europe/Berlin");
        final ZoneId kolkata = ZoneId.of("Asia/Kolkata");

        assertThat(bucketStart("2024-06-01T00:03:30Z", Duration.ofMinutes(2), null))
                .isEqualTo(Instant.parse("2024-06-01T00:02:00Z"));
        assertThat(bucketStart("2024-06-01T00:03:30Z", Duration.ofMinutes(7), null))
                .isEqualTo(Instant.parse("2024-06-01T00:00:00Z"));
        assertThat(bucketStart("2024-06-01T13:20:00Z", Duration.ofHours(5), null))
                .isEqualTo(Instant.parse("2024-06-01T13:00:00Z"));
        assertThat(bucketStart("2024-06-01T10:00:00Z", Duration.ofDays(1), berlin))
                .isEqualTo(Instant.parse("2024-05-31T22:00:00Z"));
        assertThat(bucketStart("2024-06-01T10:00:00Z", Duration.ofDays(1), null))
                .isEqualTo(Instant.parse("2024-06-01T00:00:00Z"));
        assertThat(bucketStart("2024-03-31T00:30:00Z", Duration.ofHours(3), berlin))
                .isEqualTo(Instant.parse("2024-03-30T23:00:00Z"));
        assertThat(bucketStart("2024-06-01T13:20:00Z", Duration.ofHours(5), berlin))
                .isEqualTo(Instant.parse("2024-06-01T12:00:00Z"));
        assertThat(bucketStart("2024-06-01T00:07:10Z", Duration.ofMinutes(15), kolkata))
                .isEqualTo(Instant.parse("2024-06-01T00:00:00Z"));
        assertThat(bucketStart("2024-06-01T10:00:00Z", Duration.ofDays(2), berlin))
                .isEqualTo(Instant.parse("2024-05-31T22:00:00Z"));
        // Berlin fall-back day (2024-10-27, 25h): both sides of local midnight bin to the same day.
        assertThat(bucketStart("2024-10-27T23:30:00Z", Duration.ofDays(1), berlin))
                .isEqualTo(Instant.parse("2024-10-27T23:00:00Z"));
        assertThat(bucketStart("2024-10-28T00:30:00Z", Duration.ofDays(1), berlin))
                .isEqualTo(Instant.parse("2024-10-27T23:00:00Z"));
        assertThat(bucketStart("2024-06-01T00:00:37Z", Duration.ofSeconds(5), null))
                .isEqualTo(Instant.parse("2024-06-01T00:00:35Z"));
        assertThat(bucketStart("2024-06-01T00:00:37Z", Duration.ofSeconds(10), berlin))
                .isEqualTo(Instant.parse("2024-06-01T00:00:30Z"));
        // hour bins are elapsed-based: Berlin (whole-hour offset) yields whole UTC hours across DST.
        assertThat(bucketStart("2024-03-31T01:30:00Z", Duration.ofHours(1), berlin))
                .isEqualTo(Instant.parse("2024-03-31T01:00:00Z"));
        assertThat(bucketStart("2024-03-31T02:30:00Z", Duration.ofHours(1), berlin))
                .isEqualTo(Instant.parse("2024-03-31T02:00:00Z"));
    }

    // --- aggregateBuckets (in-kernel downsampling) ---

    @Test
    public void aggregateBucketsComputesPerBucketReductions() {
        final Instant t0 = Instant.parse("2024-06-01T00:00:00Z");
        final List<TimePoint> points = List.of(
                new TimePoint(t0, 10.0),
                new TimePoint(t0.plusSeconds(3), 20.0),   // bucket :00 -> [10, 20]
                new TimePoint(t0.plusSeconds(12), 30.0),
                new TimePoint(t0.plusSeconds(15), 50.0));  // bucket :10 -> [30, 50]
        final Duration step = Duration.ofSeconds(10);
        final Instant b0 = t0;
        final Instant b1 = t0.plusSeconds(10);

        final LinkedHashMap<Instant, JsonValue> avg =
                TimeseriesComputeKernel.aggregateBuckets(points, step, Aggregation.AVG, null, null);
        assertThat(avg.get(b0).asDouble()).isEqualTo(15.0);
        assertThat(avg.get(b1).asDouble()).isEqualTo(40.0);
        assertThat(new ArrayList<>(avg.keySet())).containsExactly(b0, b1); // ascending

        assertThat(agg(points, step, Aggregation.COUNT, null).get(b0).asInt()).isEqualTo(2);
        assertThat(agg(points, step, Aggregation.SUM, null).get(b1).asDouble()).isEqualTo(80.0);
        assertThat(agg(points, step, Aggregation.MIN, null).get(b1).asDouble()).isEqualTo(30.0);
        assertThat(agg(points, step, Aggregation.MAX, null).get(b0).asDouble()).isEqualTo(20.0);
        assertThat(agg(points, step, Aggregation.FIRST, null).get(b1).asDouble()).isEqualTo(30.0);
        assertThat(agg(points, step, Aggregation.LAST, null).get(b0).asDouble()).isEqualTo(20.0);
        assertThat(TimeseriesComputeKernel.aggregateBuckets(points, step, Aggregation.PERCENTILE, null, 50.0)
                .get(b0).asDouble()).isCloseTo(15.0, within(1e-9));
        assertThat(agg(points, step, Aggregation.STDDEV, null).get(b0).asDouble())
                .isCloseTo(Math.sqrt(50.0), within(1e-9)); // sample stddev of [10, 20]
    }

    @Test
    public void aggregateBucketsStdDevOfSinglePointIsNull() {
        final Instant t0 = Instant.parse("2024-06-01T00:00:00Z");

        final LinkedHashMap<Instant, JsonValue> out = TimeseriesComputeKernel.aggregateBuckets(
                List.of(new TimePoint(t0, 10.0)), Duration.ofSeconds(10), Aggregation.STDDEV, null, null);

        assertThat(out).containsKey(t0);
        assertThat(out.get(t0)).isNull(); // matches $stdDevSamp -> null for n<2 (rendered as a gap)
    }

    @Test
    public void aggregateBucketsOutputFeedsFillBuckets() {
        final Instant t0 = Instant.parse("2024-06-01T00:00:00Z");
        final List<TimePoint> points = List.of(
                new TimePoint(t0, 10.0),
                new TimePoint(t0.plusSeconds(30), 40.0)); // buckets :10 and :20 are empty (10s step)

        final LinkedHashMap<Instant, JsonValue> byBucket = TimeseriesComputeKernel.aggregateBuckets(
                points, Duration.ofSeconds(10), Aggregation.AVG, null, null);
        final List<TimeseriesDataValue> filled = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(10), FillStrategy.LINEAR, null);

        assertThat(filled).hasSize(4);           // :00, :10(fill), :20(fill), :30
        assertThat(filled.get(1).isGap()).isTrue();
    }

    @Test
    public void aggregateBucketsRejectsWholeSeriesAggregation() {
        final List<TimePoint> onePoint =
                List.of(new TimePoint(Instant.parse("2024-06-01T00:00:00Z"), 1.0));

        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TimeseriesComputeKernel.aggregateBuckets(onePoint, Duration.ofSeconds(10),
                        Aggregation.INTEGRAL, null, null));
    }

    @Test
    public void aggregateBucketsPercentileRequiresPercentileValue() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                TimeseriesComputeKernel.aggregateBuckets(List.of(), Duration.ofSeconds(10),
                        Aggregation.PERCENTILE, null, null));
    }

    private static Instant bucketStart(final String iso, final Duration step, final ZoneId zone) {
        return TimeseriesComputeKernel.bucketStart(Instant.parse(iso), step, zone);
    }

    private static LinkedHashMap<Instant, JsonValue> agg(final List<TimePoint> points,
            final Duration step, final Aggregation aggregation, final ZoneId zone) {
        return TimeseriesComputeKernel.aggregateBuckets(points, step, aggregation, zone, null);
    }

    // --- fillBuckets ---

    @Test
    public void fillBucketsWithoutStrategyEmitsOnlyPopulatedBuckets() {
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        byBucket.put(T0, JsonValue.of(1.0));
        byBucket.put(T0.plusSeconds(15), JsonValue.of(2.0));

        final List<TimeseriesDataValue> result =
                TimeseriesComputeKernel.fillBuckets(byBucket, Duration.ofSeconds(5), null, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).isGap()).isFalse();
        assertThat(result.get(1).getTimestamp()).isEqualTo(T0.plusSeconds(15));
    }

    @Test
    public void fillBucketsLinearInterpolatesInteriorGaps() {
        // Gap between T0=12.0 and T0+25s=41.0 with a 5s step -> four interpolated interior points on
        // a 5.8/step ramp (matches the live-validated example).
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        byBucket.put(T0, JsonValue.of(12.0));
        byBucket.put(T0.plusSeconds(25), JsonValue.of(41.0));

        final List<TimeseriesDataValue> result = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(5), FillStrategy.LINEAR, null);

        assertThat(result).hasSize(6);
        assertThat(doubleAt(result, 1)).isCloseTo(17.8, within(1e-9));
        assertThat(doubleAt(result, 2)).isCloseTo(23.6, within(1e-9));
        assertThat(doubleAt(result, 3)).isCloseTo(29.4, within(1e-9));
        assertThat(doubleAt(result, 4)).isCloseTo(35.2, within(1e-9));
        // Interpolated points are flagged as gaps; the two real anchors are not.
        assertThat(result.get(0).isGap()).isFalse();
        assertThat(result.get(1).isGap()).isTrue();
        assertThat(result.get(5).isGap()).isFalse();
    }

    @Test
    public void fillBucketsPreviousAndZeroFillInteriorGaps() {
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        byBucket.put(T0, JsonValue.of(7.0));
        byBucket.put(T0.plusSeconds(10), JsonValue.of(9.0));

        final List<TimeseriesDataValue> previous = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(5), FillStrategy.PREVIOUS, null);
        assertThat(doubleAt(previous, 1)).isEqualTo(7.0); // carried forward

        final List<TimeseriesDataValue> zero = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(5), FillStrategy.ZERO, null);
        assertThat(doubleAt(zero, 1)).isEqualTo(0.0);

        final List<TimeseriesDataValue> nul = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(5), FillStrategy.NULL, null);
        assertThat(nul.get(1).isGap()).isTrue();
        assertThat(nul.get(1).getValue()).isEmpty();
    }

    @Test
    public void fillBucketsLinearFallsBackToNullGapForNonNumericEndpoints() {
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        byBucket.put(T0, JsonValue.of("not-a-number"));
        byBucket.put(T0.plusSeconds(10), JsonValue.of(9.0));

        final List<TimeseriesDataValue> result = TimeseriesComputeKernel.fillBuckets(
                byBucket, Duration.ofSeconds(5), FillStrategy.LINEAR, null);

        assertThat(result.get(1).isGap()).isTrue();
        assertThat(result.get(1).getValue()).isEmpty();
    }

    @Test
    public void fillBucketsEmptyReturnsEmpty() {
        assertThat(TimeseriesComputeKernel.fillBuckets(
                new LinkedHashMap<>(), Duration.ofSeconds(5), FillStrategy.LINEAR, null)).isEmpty();
    }

    // --- derivative / rate ---

    @Test
    public void derivativeComputesSlopePerSecondAndDropsFirstPoint() {
        final List<TimePoint> points = List.of(
                new TimePoint(T0, 10.0),
                new TimePoint(T0.plusSeconds(5), 20.0),   // +10 over 5s -> 2.0
                new TimePoint(T0.plusSeconds(10), 20.0)); // 0 over 5s -> 0.0

        final List<TimeseriesDataValue> result = TimeseriesComputeKernel.derivative(points, false);

        assertThat(result).hasSize(2);
        assertThat(doubleAt(result, 0)).isCloseTo(2.0, within(1e-9));
        assertThat(doubleAt(result, 1)).isCloseTo(0.0, within(1e-9));
        assertThat(result.get(0).getTimestamp()).isEqualTo(T0.plusSeconds(5));
    }

    @Test
    public void rateTreatsValueDecreaseAsCounterReset() {
        final List<TimePoint> points = List.of(
                new TimePoint(T0, 100.0),
                new TimePoint(T0.plusSeconds(10), 40.0)); // decrease -> reset: 40/10 = 4.0

        final List<TimeseriesDataValue> result = TimeseriesComputeKernel.derivative(points, true);

        assertThat(doubleAt(result, 0)).isCloseTo(4.0, within(1e-9));
    }

    @Test
    public void derivativeSkipsNonAdvancingTimestamps() {
        final List<TimePoint> points = List.of(
                new TimePoint(T0, 10.0),
                new TimePoint(T0, 20.0)); // dt == 0 -> skipped

        assertThat(TimeseriesComputeKernel.derivative(points, false)).isEmpty();
    }

    // --- integral ---

    @Test
    public void integralComputesTrapezoidalAreaStampedAtLastPoint() {
        final List<TimePoint> points = List.of(
                new TimePoint(T0, 2.0),
                new TimePoint(T0.plusSeconds(10), 4.0)); // trapezoid: (2+4)/2 * 10 = 30

        final List<TimeseriesDataValue> result = TimeseriesComputeKernel.integral(points);

        assertThat(result).hasSize(1);
        assertThat(doubleAt(result, 0)).isCloseTo(30.0, within(1e-9));
        assertThat(result.get(0).getTimestamp()).isEqualTo(T0.plusSeconds(10));
    }

    @Test
    public void integralOfEmptyIsEmpty() {
        assertThat(TimeseriesComputeKernel.integral(List.of())).isEmpty();
    }

    // --- percentile ---

    @Test
    public void percentileInterpolatesBetweenRanks() {
        final List<Double> values = List.of(1.0, 2.0, 3.0, 4.0);
        assertThat(TimeseriesComputeKernel.percentile(values, 50)).isCloseTo(2.5, within(1e-9));
        assertThat(TimeseriesComputeKernel.percentile(values, 0)).isCloseTo(1.0, within(1e-9));
        assertThat(TimeseriesComputeKernel.percentile(values, 100)).isCloseTo(4.0, within(1e-9));
    }

    @Test
    public void percentileOfSingleValueIsThatValue() {
        assertThat(TimeseriesComputeKernel.percentile(List.of(42.0), 95)).isEqualTo(42.0);
    }

    @Test
    public void percentileDoesNotMutateInput() {
        final List<Double> values = new java.util.ArrayList<>(List.of(3.0, 1.0, 2.0));
        TimeseriesComputeKernel.percentile(values, 50);
        assertThat(values).containsExactly(3.0, 1.0, 2.0);
    }

    @Test
    public void percentileOfEmptyThrows() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TimeseriesComputeKernel.percentile(List.of(), 50));
    }

    private static double doubleAt(final List<TimeseriesDataValue> data, final int index) {
        return data.get(index).getValue().orElseThrow().asDouble();
    }
}
