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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.FillStrategy;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;

/**
 * Backend-neutral reference implementations of the timeseries computations that cannot (or should
 * not) be delegated to a specific database: bucket-grid stepping, gap fill, and the "advanced"
 * aggregations that are derived from fetched points ({@code derivative}, {@code rate},
 * {@code integral}, {@code percentile}).
 * <p>
 * These functions define the <em>meaning</em> of each operation once, so that every
 * {@link org.eclipse.ditto.timeseries.api.TimeseriesAdapter} produces identical results regardless
 * of the underlying store. An adapter fetches/reduces raw data with its own database, then hands
 * the small result to this kernel for the final, portable calculation ("the database reduces, the
 * kernel decides"). The kernel is also the reference answer any native push-down implementation is
 * expected to match.
 * <p>
 * All methods are pure (no I/O, no shared mutable state) and therefore thread-safe.
 *
 * @since 4.0.0
 */
public final class TimeseriesComputeKernel {

    private TimeseriesComputeKernel() {
        throw new AssertionError();
    }

    // --- Bucket-grid stepping ---

    /**
     * The {@code (unit, binSize)} pair a fixed-step bucket grid bins by, derived from a step
     * {@link Duration}. The same pair drives both a backend's calendar bucketing (e.g. MongoDB's
     * {@code $dateTrunc}) and the in-kernel gap-fill grid (see {@link #nextBucket}), so the two
     * stay aligned.
     *
     * @param unit the calendar unit ({@code day}, {@code hour}, {@code minute} or {@code second}).
     * @param binSize the number of {@code unit}s per bucket.
     */
    public record StepUnit(String unit, long binSize) {}

    /**
     * Derives the coarsest {@link StepUnit} that divides the given step exactly.
     *
     * @param step the bucket width; must be a whole number of seconds.
     * @return the {@code (unit, binSize)} pair the grid bins by.
     * @throws NullPointerException if {@code step} is {@code null}.
     */
    public static StepUnit stepUnitFor(final Duration step) {
        checkNotNull(step, "step");
        final long seconds = step.getSeconds();
        if (seconds % 86400 == 0) {
            return new StepUnit("day", seconds / 86400);
        } else if (seconds % 3600 == 0) {
            return new StepUnit("hour", seconds / 3600);
        } else if (seconds % 60 == 0) {
            return new StepUnit("minute", seconds / 60);
        } else {
            return new StepUnit("second", seconds);
        }
    }

    /**
     * Advances {@code cursor} to the next bucket start. Without a timezone the step is a fixed
     * {@link Duration} (exact for UTC). With a timezone the step is taken in that zone's calendar,
     * so day/hour boundaries track wall-clock time across DST transitions — mirroring how a
     * tz-aware calendar truncation aligns buckets (a "day" bucket spans 23h or 25h around a
     * transition, not a fixed 24h).
     *
     * @param cursor the current bucket start.
     * @param step the bucket width.
     * @param zone the timezone the buckets are aligned to, or {@code null} for UTC / fixed stepping.
     * @return the start of the next bucket.
     * @throws NullPointerException if {@code cursor} or {@code step} is {@code null}.
     */
    public static Instant nextBucket(final Instant cursor, final Duration step,
            @Nullable final ZoneId zone) {
        checkNotNull(cursor, "cursor");
        checkNotNull(step, "step");
        if (zone == null) {
            return cursor.plus(step);
        }
        final StepUnit stepUnit = stepUnitFor(step);
        final ZonedDateTime zoned = cursor.atZone(zone);
        final ZonedDateTime next = switch (stepUnit.unit()) {
            case "day" -> zoned.plusDays(stepUnit.binSize());
            case "hour" -> zoned.plusHours(stepUnit.binSize());
            case "minute" -> zoned.plusMinutes(stepUnit.binSize());
            default -> zoned.plusSeconds(stepUnit.binSize());
        };
        return next.toInstant();
    }

    /**
     * Computes the bucket start that a timestamp falls into, matching MongoDB's {@code $dateTrunc}
     * binning so that in-kernel downsampling (over scanned points) and a backend's native
     * downsampling produce the same grid.
     * <p>
     * The reference point is {@code 2000-01-01T00:00:00} in the target zone (UTC when {@code zone}
     * is {@code null}). {@code second}/{@code minute}/{@code hour} bins are elapsed-based (a fixed
     * {@code binSize × unit} duration from the reference instant — the zone only shifts that
     * instant); {@code day} bins are calendar-based (local midnight in the zone), so a bin spans
     * 23h or 25h across a DST transition.
     *
     * @param t the timestamp to bin.
     * @param step the bucket width.
     * @param zone the zone the buckets align to, or {@code null} for UTC.
     * @return the start instant of the bucket containing {@code t}.
     * @throws NullPointerException if {@code t} or {@code step} is {@code null}.
     */
    public static Instant bucketStart(final Instant t, final Duration step,
            @Nullable final ZoneId zone) {

        checkNotNull(t, "t");
        checkNotNull(step, "step");
        final ZoneId z = zone != null ? zone : ZoneOffset.UTC;
        final StepUnit stepUnit = stepUnitFor(step);
        final ZonedDateTime ref = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, z);
        if ("day".equals(stepUnit.unit())) {
            final long daysBetween = ChronoUnit.DAYS.between(ref.toLocalDate(), t.atZone(z).toLocalDate());
            final long binDays = Math.floorDiv(daysBetween, stepUnit.binSize()) * stepUnit.binSize();
            return ref.toLocalDate().plusDays(binDays).atStartOfDay(z).toInstant();
        }
        final long unitSeconds = switch (stepUnit.unit()) {
            case "hour" -> 3600L;
            case "minute" -> 60L;
            default -> 1L;
        };
        final long binSeconds = stepUnit.binSize() * unitSeconds;
        final long refEpoch = ref.toInstant().getEpochSecond();
        final long elapsed = t.getEpochSecond() - refEpoch;
        return Instant.ofEpochSecond(refEpoch + Math.floorDiv(elapsed, binSeconds) * binSeconds);
    }

    // --- Bucketed aggregation (in-kernel downsampling) ---

    /**
     * Downsamples scanned points into {@code step} buckets and reduces each bucket with the given
     * {@code aggregation}, returning bucket-start &rarr; aggregated value in ascending order — the
     * same shape a backend's native downsampling produces, ready for {@link #fillBuckets}. This is
     * how a scan-only backend (one that cannot push bucketing down) still answers bucketed queries:
     * the kernel does the grouping the database would otherwise do.
     * <p>
     * Supports the single-value bucket reductions {@code AVG}, {@code MIN}, {@code MAX},
     * {@code SUM}, {@code COUNT}, {@code FIRST}, {@code LAST}, {@code STDDEV} (sample standard
     * deviation — {@code null} for a single-point bucket, matching {@code $stdDevSamp}) and
     * {@code PERCENTILE}. The whole-series aggregations ({@code DERIVATIVE}, {@code RATE},
     * {@code INTEGRAL}) are not bucket reductions and are rejected. A {@code null} bucket value
     * (e.g. single-point {@code STDDEV}) is emitted and rendered as a gap by {@link #fillBuckets}.
     *
     * @param points the source points, ascending by time.
     * @param step the bucket width.
     * @param aggregation the per-bucket reduction.
     * @param zone the zone the buckets align to, or {@code null} for UTC.
     * @param percentile the percentile (0–100), required when {@code aggregation} is
     * {@code PERCENTILE}; ignored otherwise.
     * @return an ascending map of bucket start to aggregated value (values may be {@code null}).
     * @throws NullPointerException if {@code points}, {@code step} or {@code aggregation} is
     * {@code null}.
     * @throws IllegalArgumentException if {@code aggregation} is not a bucket reduction, or is
     * {@code PERCENTILE} without a {@code percentile}.
     */
    public static LinkedHashMap<Instant, JsonValue> aggregateBuckets(final List<TimePoint> points,
            final Duration step,
            final Aggregation aggregation,
            @Nullable final ZoneId zone,
            @Nullable final Double percentile) {

        checkNotNull(points, "points");
        checkNotNull(step, "step");
        checkNotNull(aggregation, "aggregation");
        if (aggregation == Aggregation.PERCENTILE && percentile == null) {
            throw new IllegalArgumentException("The percentile aggregation requires a percentile value.");
        }

        // Group ascending points by bucket start; insertion order of the outer map stays ascending
        // because the points are ascending, which is exactly the order fillBuckets expects.
        final LinkedHashMap<Instant, List<Double>> grouped = new LinkedHashMap<>();
        for (final TimePoint point : points) {
            grouped.computeIfAbsent(bucketStart(point.time(), step, zone), k -> new ArrayList<>())
                    .add(point.value());
        }
        final LinkedHashMap<Instant, JsonValue> out = new LinkedHashMap<>();
        for (final Map.Entry<Instant, List<Double>> entry : grouped.entrySet()) {
            out.put(entry.getKey(), reduceBucket(entry.getValue(), aggregation, percentile));
        }
        return out;
    }

    @Nullable
    private static JsonValue reduceBucket(final List<Double> values, final Aggregation aggregation,
            @Nullable final Double percentile) {

        switch (aggregation) {
            case COUNT:
                return JsonValue.of(values.size());
            case SUM:
                return JsonValue.of(sum(values));
            case AVG:
                return JsonValue.of(sum(values) / values.size());
            case MIN:
                return JsonValue.of(Collections.min(values));
            case MAX:
                return JsonValue.of(Collections.max(values));
            case FIRST:
                return JsonValue.of(values.get(0));
            case LAST:
                return JsonValue.of(values.get(values.size() - 1));
            case STDDEV:
                return sampleStdDev(values);
            case PERCENTILE:
                return JsonValue.of(percentile(values, percentile));
            default:
                throw new IllegalArgumentException(
                        "Aggregation <" + aggregation.getName() + "> is not a bucket reduction.");
        }
    }

    private static double sum(final List<Double> values) {
        double total = 0.0;
        for (final double v : values) {
            total += v;
        }
        return total;
    }

    /** Sample standard deviation ({@code n-1} denominator); {@code null} for fewer than 2 points. */
    @Nullable
    private static JsonValue sampleStdDev(final List<Double> values) {
        final int n = values.size();
        if (n < 2) {
            return null;
        }
        final double mean = sum(values) / n;
        double sumSq = 0.0;
        for (final double v : values) {
            final double d = v - mean;
            sumSq += d * d;
        }
        return JsonValue.of(Math.sqrt(sumSq / (n - 1)));
    }

    // --- Gap fill ---

    /**
     * Materialises the bucket grid from a map of populated bucket starts to aggregated values.
     * With no fill strategy only populated buckets are emitted; with a strategy, interior gaps
     * between populated buckets are filled per {@link FillStrategy}.
     * <p>
     * The grid is driven off the populated bucket starts rather than a free-running cursor: each
     * {@code [present[i-1], present[i]]} segment is walked by {@link #nextBucket} and always closed
     * by emitting {@code present[i]} exactly. This keeps the grid locked to the backend's bucket
     * alignment — including tz/DST-aware boundaries — so a populated bucket is never skipped even
     * if a calendar step would otherwise drift around a transition. Filled points are flagged as
     * gaps ({@link TimeseriesDataValue#isGap()}); real buckets are not.
     *
     * @param byBucket populated bucket start &rarr; aggregated value (value may be {@code null}),
     * in ascending bucket order.
     * @param step the bucket width.
     * @param fill the fill strategy, or {@code null} to emit only populated buckets.
     * @param zone the timezone the buckets were aligned to ({@code null} for UTC); controls whether
     * stepping is calendar-aware (see {@link #nextBucket}).
     * @return the materialised, ascending list of data values.
     * @throws NullPointerException if {@code byBucket} or {@code step} is {@code null}.
     */
    public static List<TimeseriesDataValue> fillBuckets(
            final LinkedHashMap<Instant, JsonValue> byBucket,
            final Duration step,
            @Nullable final FillStrategy fill,
            @Nullable final ZoneId zone) {

        checkNotNull(byBucket, "byBucket");
        checkNotNull(step, "step");
        final List<TimeseriesDataValue> data = new ArrayList<>();
        if (byBucket.isEmpty()) {
            return data;
        }
        final List<Instant> present = new ArrayList<>(byBucket.keySet());
        if (fill == null) {
            for (final Instant bucket : present) {
                data.add(toValue(bucket, byBucket.get(bucket)));
            }
            return data;
        }

        data.add(toValue(present.get(0), byBucket.get(present.get(0))));
        for (int i = 1; i < present.size(); i++) {
            final Instant segmentStart = present.get(i - 1);
            final Instant segmentEnd = present.get(i);
            final JsonValue startValue = byBucket.get(segmentStart);
            final JsonValue endValue = byBucket.get(segmentEnd);
            Instant cursor = nextBucket(segmentStart, step, zone);
            while (cursor.isBefore(segmentEnd)) {
                if (fill == FillStrategy.LINEAR) {
                    data.add(linearFill(cursor, segmentStart, startValue, segmentEnd, endValue));
                } else {
                    data.add(fillValue(cursor, fill, startValue));
                }
                cursor = nextBucket(cursor, step, zone);
            }
            data.add(toValue(segmentEnd, endValue));
        }
        return data;
    }

    private static TimeseriesDataValue toValue(final Instant t, @Nullable final JsonValue v) {
        return v == null ? TimeseriesDataValue.gap(t, null) : TimeseriesDataValue.of(t, v);
    }

    private static TimeseriesDataValue fillValue(final Instant t, final FillStrategy fill,
            @Nullable final JsonValue previous) {
        return switch (fill) {
            case ZERO -> TimeseriesDataValue.gap(t, JsonValue.of(0));
            case PREVIOUS -> TimeseriesDataValue.gap(t, previous);
            case NULL -> TimeseriesDataValue.gap(t, null);
            // LINEAR needs both surrounding anchors, so it is interpolated in fillBuckets directly.
            case LINEAR -> throw new IllegalStateException(
                    "LINEAR fill is interpolated in fillBuckets and must not reach fillValue.");
        };
    }

    /**
     * Linearly interpolates the value at gap instant {@code t} between the surrounding populated
     * buckets {@code (t0, v0)} and {@code (t1, v1)}. Interpolation is only defined for numeric
     * endpoints; for non-numeric or missing neighbours it falls back to a {@code null} gap (the
     * same shape {@link FillStrategy#NULL} would produce) rather than fabricating a value.
     */
    private static TimeseriesDataValue linearFill(final Instant t,
            final Instant t0, @Nullable final JsonValue v0,
            final Instant t1, @Nullable final JsonValue v1) {

        if (v0 == null || v1 == null || !v0.isNumber() || !v1.isNumber()) {
            return TimeseriesDataValue.gap(t, null);
        }
        final double spanMillis = Duration.between(t0, t1).toMillis();
        final double fraction =
                spanMillis == 0.0 ? 0.0 : Duration.between(t0, t).toMillis() / spanMillis;
        final double y0 = v0.asDouble();
        final double y1 = v1.asDouble();
        return TimeseriesDataValue.gap(t, JsonValue.of(y0 + (y1 - y0) * fraction));
    }

    // --- Advanced aggregations derived from fetched points ---

    /**
     * An immutable {@code (timestamp, numeric value)} pair — the input shape for the point-derived
     * aggregations below. An adapter maps its fetched (and optionally pre-downsampled) rows to a
     * list of these before handing them to the kernel.
     *
     * @param time the observation timestamp.
     * @param value the numeric value at {@code time}.
     */
    public record TimePoint(Instant time, double value) {}

    /**
     * Computes the discrete derivative of consecutive points: {@code (v[n]-v[n-1])/dt} in seconds.
     * {@code rate} is the non-negative variant that treats a value decrease as a counter reset
     * ({@code v[n]/dt}). Points that are not strictly after their predecessor ({@code dt <= 0}) are
     * skipped. The first point has no predecessor, so the result has at most one fewer point than
     * the input; each result is stamped at the later of the two timestamps.
     *
     * @param points the source points, ascending by time.
     * @param rate {@code true} for the counter-reset (rate) variant, {@code false} for a plain
     * derivative.
     * @return the derivative series (never {@code null}).
     * @throws NullPointerException if {@code points} is {@code null}.
     */
    public static List<TimeseriesDataValue> derivative(final List<TimePoint> points,
            final boolean rate) {

        checkNotNull(points, "points");
        final List<TimeseriesDataValue> data = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            final TimePoint prev = points.get(i - 1);
            final TimePoint cur = points.get(i);
            final double dt = (cur.time().toEpochMilli() - prev.time().toEpochMilli()) / 1000.0;
            if (dt <= 0) {
                continue;
            }
            final double d = (rate && cur.value() < prev.value())
                    ? cur.value() / dt
                    : (cur.value() - prev.value()) / dt;
            data.add(TimeseriesDataValue.of(cur.time(), JsonValue.of(d)));
        }
        return data;
    }

    /**
     * Computes the trapezoidal area under the curve over consecutive points, in value-seconds. A
     * single whole-range result is returned, stamped at the last observation; an empty input yields
     * an empty result.
     *
     * @param points the source points, ascending by time.
     * @return a list holding the single integral result, or empty if {@code points} is empty.
     * @throws NullPointerException if {@code points} is {@code null}.
     */
    public static List<TimeseriesDataValue> integral(final List<TimePoint> points) {
        checkNotNull(points, "points");
        double integral = 0;
        for (int i = 1; i < points.size(); i++) {
            final TimePoint prev = points.get(i - 1);
            final TimePoint cur = points.get(i);
            final double dt = (cur.time().toEpochMilli() - prev.time().toEpochMilli()) / 1000.0;
            integral += (cur.value() + prev.value()) / 2.0 * dt;
        }
        final List<TimeseriesDataValue> data = new ArrayList<>();
        if (!points.isEmpty()) {
            data.add(TimeseriesDataValue.of(points.get(points.size() - 1).time(),
                    JsonValue.of(integral)));
        }
        return data;
    }

    /**
     * Computes the {@code p}-th percentile (0–100) of the given values using linear interpolation
     * between the two closest ranks. Portable across backends and independent of any native
     * percentile accumulator. The input is defensively copied and sorted; it is not mutated.
     *
     * @param values the values to compute the percentile over; must be non-empty.
     * @param p the percentile in {@code [0, 100]}.
     * @return the interpolated percentile value.
     * @throws NullPointerException if {@code values} is {@code null}.
     * @throws IllegalArgumentException if {@code values} is empty.
     */
    public static double percentile(final List<Double> values, final double p) {
        checkNotNull(values, "values");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute a percentile of an empty value list.");
        }
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        final double rank = (p / 100.0) * (sorted.size() - 1);
        final int lo = (int) Math.floor(rank);
        final int hi = (int) Math.ceil(rank);
        if (lo == hi) {
            return sorted.get(lo);
        }
        return sorted.get(lo) + (rank - lo) * (sorted.get(hi) - sorted.get(lo));
    }
}
