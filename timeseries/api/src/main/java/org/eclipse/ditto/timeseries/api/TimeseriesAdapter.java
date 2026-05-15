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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
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
}
