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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel.TimePoint;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;

/**
 * Executes a {@link TimeseriesQuery} against any {@link TimeseriesAdapter} by choosing, per query,
 * between two equivalent execution paths:
 * <ul>
 *   <li><b>push-down</b> — when the adapter's {@link Capabilities} say it can compute the requested
 *       bucketed aggregation natively, the whole query is delegated to
 *       {@link TimeseriesAdapter#query(TimeseriesQuery)} so the backend does the heavy lifting; and</li>
 *   <li><b>portable</b> — otherwise the planner fetches raw points via
 *       {@link TimeseriesAdapter#scan} and computes the result in the shared
 *       {@link TimeseriesComputeKernel} (bucket &rarr; aggregate &rarr; fill, or a window
 *       aggregation).</li>
 * </ul>
 * Both paths produce the same {@link TimeseriesQueryResult}s, so a scan-only backend is fully
 * functional (portable path) and a capable backend is faster (push-down path) — without the caller
 * knowing which ran. The kernel is the reference answer, so results stay identical across backends.
 * <p>
 * The push-down decision is query-level (a query carries a single aggregation for all paths), which
 * keeps the choice all-or-nothing and avoids re-running paths.
 *
 * @since 4.0.0
 */
public final class TimeseriesQueryPlanner {

    private final TimeseriesAdapter adapter;

    /**
     * @param adapter the backend adapter to execute against.
     * @throws NullPointerException if {@code adapter} is {@code null}.
     */
    public TimeseriesQueryPlanner(final TimeseriesAdapter adapter) {
        this.adapter = checkNotNull(adapter, "adapter");
    }

    /**
     * Executes the query, returning one result per requested path in request order.
     *
     * @param query the query to execute.
     * @return a stage completing with the per-path results.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    public CompletionStage<List<TimeseriesQueryResult>> execute(final TimeseriesQuery query) {
        checkNotNull(query, "query");
        if (adapter.capabilities().supportsNativeQuery()) {
            // The backend can execute the whole query itself — delegate (fast path, and for a
            // complete backend like MongoDB this makes the planner transparent: identical behavior
            // and metadata to calling the adapter directly).
            return adapter.query(query);
        }
        final List<JsonPointer> paths = query.getPaths();
        if (paths.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        final List<CompletionStage<TimeseriesQueryResult>> perPath = new ArrayList<>(paths.size());
        for (final JsonPointer path : paths) {
            final int limit = query.getLimit().orElse(0);
            perPath.add(adapter.scan(query.getThingId(), path, query.getFrom(), query.getTo(), limit)
                    .thenApply(points -> computeInKernel(query, path, points)));
        }
        return collectInOrder(perPath);
    }

    /** Computes one path's result from scanned points using only the kernel (portable path). */
    private static TimeseriesQueryResult computeInKernel(final TimeseriesQuery query,
            final JsonPointer path, final List<TimeseriesDataValue> points) {

        final ZoneId tz = query.getTimezone().orElse(null);
        final Aggregation aggregation = query.getAggregation().orElse(null);
        final List<TimeseriesDataValue> data;
        if (aggregation == null) {
            data = points; // raw read: pass the scanned points straight through
        } else if (aggregation.requiresStep() && query.getStep().isPresent()) {
            final Duration step = query.getStep().get();
            final LinkedHashMap<Instant, JsonValue> byBucket = TimeseriesComputeKernel.aggregateBuckets(
                    toTimePoints(points), step, aggregation, tz, query.getPercentile().orElse(null));
            data = TimeseriesComputeKernel.fillBuckets(byBucket, step,
                    query.getFillStrategy().orElse(null), tz);
        } else {
            data = computeWindowAggregation(query, aggregation, points, tz);
        }
        final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), null, inferDataType(data));
        return TimeseriesQueryResult.of(query.getThingId(), path, query, meta, data);
    }

    private static List<TimeseriesDataValue> computeWindowAggregation(final TimeseriesQuery query,
            final Aggregation aggregation, final List<TimeseriesDataValue> points,
            final ZoneId tz) {

        switch (aggregation) {
            case DERIVATIVE:
                return TimeseriesComputeKernel.derivative(toTimePoints(points), false);
            case RATE:
                return TimeseriesComputeKernel.derivative(toTimePoints(points), true);
            case INTEGRAL:
                return TimeseriesComputeKernel.integral(toTimePoints(points));
            case PERCENTILE:
                return percentile(query, points, tz);
            default:
                // A bucketed aggregation without a step is rejected by the model layer before here.
                throw new IllegalStateException(
                        "Aggregation <" + aggregation.getName() + "> requires a step.");
        }
    }

    private static List<TimeseriesDataValue> percentile(final TimeseriesQuery query,
            final List<TimeseriesDataValue> points, final ZoneId tz) {

        final double p = query.getPercentile().orElseThrow(() -> new IllegalStateException(
                "The percentile aggregation requires a percentile value."));
        if (query.getStep().isPresent()) {
            final Duration step = query.getStep().get();
            final LinkedHashMap<Instant, JsonValue> byBucket = TimeseriesComputeKernel.aggregateBuckets(
                    toTimePoints(points), step, Aggregation.PERCENTILE, tz, p);
            return TimeseriesComputeKernel.fillBuckets(byBucket, step,
                    query.getFillStrategy().orElse(null), tz);
        }
        // Whole-range percentile: a single point stamped at the range start (matches the native path).
        final List<Double> values = new ArrayList<>();
        for (final TimePoint point : toTimePoints(points)) {
            values.add(point.value());
        }
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        return List.of(TimeseriesDataValue.of(query.getFrom(),
                JsonValue.of(TimeseriesComputeKernel.percentile(values, p))));
    }

    private static List<TimePoint> toTimePoints(final List<TimeseriesDataValue> points) {
        final List<TimePoint> out = new ArrayList<>(points.size());
        for (final TimeseriesDataValue point : points) {
            point.getValue()
                    .filter(JsonValue::isNumber)
                    .ifPresent(value -> out.add(new TimePoint(point.getTimestamp(), value.asDouble())));
        }
        return out;
    }

    private static String inferDataType(final List<TimeseriesDataValue> data) {
        for (final TimeseriesDataValue value : data) {
            final JsonValue jsonValue = value.getValue().orElse(null);
            if (jsonValue == null) {
                continue;
            }
            if (jsonValue.isNumber()) {
                return "number";
            }
            if (jsonValue.isString()) {
                return "string";
            }
            if (jsonValue.isBoolean()) {
                return "boolean";
            }
        }
        return "null";
    }

    private static CompletionStage<List<TimeseriesQueryResult>> collectInOrder(
            final List<CompletionStage<TimeseriesQueryResult>> stages) {

        final CompletableFuture<?>[] array = stages.stream()
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(array).thenApply(ignored -> {
            final List<TimeseriesQueryResult> ordered = new ArrayList<>(stages.size());
            for (final CompletionStage<TimeseriesQueryResult> stage : stages) {
                ordered.add(stage.toCompletableFuture().join());
            }
            return Collections.unmodifiableList(ordered);
        });
    }
}
