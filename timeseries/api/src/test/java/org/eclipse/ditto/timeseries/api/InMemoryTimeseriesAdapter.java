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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;

/**
 * A minimal, in-memory {@link TimeseriesAdapter} used as a reference "second backend" in tests. It
 * implements only the {@link #scan} primitive (plus lifecycle/write) and declares the default
 * {@linkplain Capabilities#minimal() minimal} capabilities — so it proves that the
 * {@link TimeseriesQueryPlanner} can make a scan-only backend fully queryable via the shared kernel,
 * with results that must match a capable backend such as MongoDB.
 * <p>
 * Its {@link #query} is implemented by delegating to the planner; this is recursion-safe precisely
 * because the adapter advertises no push-down, so the planner always takes the scan+kernel path and
 * never calls back into {@code query}.
 */
final class InMemoryTimeseriesAdapter implements TimeseriesAdapter {

    private final Map<String, List<TimeseriesDataValue>> series = new HashMap<>();

    /** Test helper: append a numeric point to a series. */
    void ingest(final ThingId thingId, final JsonPointer path, final Instant timestamp,
            final double value) {
        series.computeIfAbsent(key(thingId, path), k -> new ArrayList<>())
                .add(TimeseriesDataValue.of(timestamp, JsonValue.of(value)));
    }

    @Override
    public Capabilities capabilities() {
        return Capabilities.minimal(); // scan-only: the planner must drive it via scan + kernel
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
        series.computeIfAbsent(key(dataPoint.getThingId(), dataPoint.getPath()), k -> new ArrayList<>())
                .add(TimeseriesDataValue.of(dataPoint.getTimestamp(), dataPoint.getValue()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
        return new TimeseriesQueryPlanner(this).execute(query);
    }

    @Override
    public CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId,
            final JsonPointer path, final Instant from, final Instant to, final int limit) {

        final List<TimeseriesDataValue> out = new ArrayList<>();
        for (final TimeseriesDataValue value : series.getOrDefault(key(thingId, path), List.of())) {
            final Instant t = value.getTimestamp();
            if (!t.isBefore(from) && t.isBefore(to)) { // [from, to)
                out.add(value);
            }
        }
        out.sort(Comparator.comparing(TimeseriesDataValue::getTimestamp));
        final int cap = (limit <= 0) ? out.size() : Math.min(limit, out.size());
        return CompletableFuture.completedFuture(List.copyOf(out.subList(0, cap)));
    }

    private static String key(final ThingId thingId, final JsonPointer path) {
        return thingId + "|" + path;
    }
}
