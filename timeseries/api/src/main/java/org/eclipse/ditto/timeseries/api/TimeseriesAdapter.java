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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;

/**
 * Service Provider Interface for timeseries database backends.
 * <p>
 * Implementations integrate the Timeseries service with a concrete backend (MongoDB Time Series,
 * IoTDB, TimescaleDB, InfluxDB, etc.). The default Ditto distribution ships a MongoDB Time Series
 * adapter; custom backends are integrated by implementing this interface and registering the
 * implementation via the configured {@code ditto.timeseries.adapter.type}.
 * <p>
 * The contract is intentionally narrow for the Phase 1 (MVP) surface: lifecycle, write,
 * single-Thing query, and health. Cross-Thing aggregation, retention, statistics, and schema
 * management are exposed via {@code default} methods that throw
 * {@link UnsupportedOperationException}; adapters opt in by overriding them in later phases.
 *
 * <h2>Concurrency</h2>
 * Implementations are expected to be thread-safe. All asynchronous methods return a
 * {@link CompletionStage} that completes on a thread the caller should not block. Per Ditto's
 * actor-concurrency rules, callers route the result back through {@code Patterns.pipe(...)}
 * before mutating actor state.
 *
 * @since 4.0.0
 */
public interface TimeseriesAdapter {

    // --- Capabilities ---

    /**
     * Declares what this adapter can compute natively (push down into its backend) versus what must
     * be computed in the shared compute kernel. A future query planner reads this to route each
     * operation to the fast (push-down) or portable (scan + kernel) path.
     * <p>
     * The default is {@link Capabilities#nativeQuery()} — the planner delegates whole queries to
     * this adapter's {@link #query(TimeseriesQuery)} (which is a required method, so every adapter
     * has it). A scan-only backend instead overrides this to return {@link Capabilities#minimal()}
     * and implements {@link #scan}, letting the planner compute everything in the kernel. Advertising
     * a capability must never change results, only where they are computed.
     *
     * @return the adapter's capabilities.
     */
    default Capabilities capabilities() {
        return Capabilities.nativeQuery();
    }

    // --- Lifecycle ---

    /**
     * Initialises the adapter with the given configuration. Called once during service start-up
     * before any other method.
     *
     * @param config the resolved adapter configuration.
     * @return a {@code CompletionStage} that completes once the adapter is ready to serve writes
     * and queries.
     * @throws NullPointerException if {@code config} is {@code null}.
     */
    CompletionStage<Void> initialize(TimeseriesAdapterConfig config);

    /**
     * Releases all resources held by the adapter (connection pools, background tasks). Idempotent.
     *
     * @return a {@code CompletionStage} that completes once shutdown is finished.
     */
    CompletionStage<Void> shutdown();

    /**
     * Returns the current health of the adapter. Should be cheap; not necessarily issuing a
     * round-trip to the backend on every call.
     *
     * @return the current health status.
     */
    HealthStatus getHealth();

    // --- Ingestion ---

    /**
     * Writes a single data point to the backend.
     *
     * @param dataPoint the data point to write.
     * @return a {@code CompletionStage} that completes once the write is durable.
     * @throws NullPointerException if {@code dataPoint} is {@code null}.
     */
    CompletionStage<Void> write(TimeseriesDataPoint dataPoint);

    /**
     * Writes a batch of data points. Implementations should aim to perform a single round-trip per
     * batch where the backend supports it. The default implementation falls back to looping over
     * {@link #write(TimeseriesDataPoint)} and is therefore correct but unoptimised.
     *
     * @param dataPoints the data points to write. May be empty (no-op).
     * @return a {@code CompletionStage} that completes once all writes are durable.
     * @throws NullPointerException if {@code dataPoints} is {@code null} or contains a {@code null}
     * element.
     */
    default CompletionStage<Void> writeBatch(final List<TimeseriesDataPoint> dataPoints) {
        checkNotNull(dataPoints, "dataPoints");
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final TimeseriesDataPoint dataPoint : dataPoints) {
            checkNotNull(dataPoint, "dataPoint in batch");
            chain = chain.thenCompose(ignored -> write(dataPoint));
        }
        return chain;
    }

    // --- Query ---

    /**
     * Executes a single-Thing timeseries query and returns one result per requested path.
     *
     * @param query the query to execute.
     * @return a {@code CompletionStage} that completes with the per-path results.
     * @throws NullPointerException if {@code query} is {@code null}.
     */
    CompletionStage<List<TimeseriesQueryResult>> query(TimeseriesQuery query);

    /**
     * The universal read primitive: returns the raw, non-gap data points for one series
     * ({@code thingId} + {@code path}) in the half-open time range {@code [from, to)}, ordered
     * ascending by timestamp, up to {@code limit} points.
     * <p>
     * This is the lowest common denominator every timeseries backend can satisfy, and it is what a
     * query planner builds on to compute any operation portably in the shared compute kernel (bucket
     * / aggregate / fill) when the backend cannot push that operation down. An adapter that
     * implements {@code scan} can be driven entirely by the planner; the default throws
     * {@link UnsupportedOperationException} so a backend that only implements the monolithic
     * {@link #query(TimeseriesQuery)} still compiles.
     *
     * @param thingId the Thing whose series to scan.
     * @param path the feature-property pointer identifying the series.
     * @param from the inclusive start of the time range.
     * @param to the exclusive end of the time range.
     * @param limit the maximum number of points to return; a non-positive value means "the adapter's
     * configured ceiling".
     * @return a {@code CompletionStage} completing with the ascending points (never {@code null}).
     * @throws NullPointerException if {@code thingId}, {@code path}, {@code from} or {@code to} is
     * {@code null}.
     * @throws UnsupportedOperationException if this adapter does not implement {@code scan}.
     */
    default CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId,
            final JsonPointer path, final Instant from, final Instant to, final int limit) {
        throw new UnsupportedOperationException(
                getClass().getName() + " does not implement scan(...).");
    }
}
