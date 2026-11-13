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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.r2dbc.proxy.core.MethodExecutionInfo;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.listener.ProxyMethodExecutionListener;

/**
 * An r2dbc-proxy listener that feeds the neutral {@link PostgresMetrics} family. Since there is no upstream
 * {@code kamon-r2dbc} for Kamon 2.8.1, the wiring is done here.
 * <ul>
 *     <li><strong>Command timer</strong> — {@link #afterQuery(QueryExecutionInfo)} records {@link
 *     PostgresMetrics#COMMAND_DURATION} using {@link QueryExecutionInfo#getExecuteDuration()}, tagged with the neutral
 *     op-kind (derived from the SQL leading keyword) and {@code status=success|error} (from
 *     {@link QueryExecutionInfo#isSuccess()}). Recorded on success <em>and</em> error.</li>
 *     <li><strong>Acquire latency</strong> — {@link #afterCreateOnConnectionFactory(MethodExecutionInfo)} records
 *     {@link PostgresMetrics#ACQUIRE_DURATION} ({@code PoolMetrics} exposes no acquire wait-time).</li>
 * </ul>
 */
public final class R2dbcMetricsListener implements ProxyMethodExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(R2dbcMetricsListener.class);

    private final PostgresMetricsRecorder recorder;

    private R2dbcMetricsListener(final PostgresMetricsRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * @return a listener recording into Kamon via {@link PostgresMetricsRecorder#kamon()}.
     */
    public static R2dbcMetricsListener kamon() {
        return new R2dbcMetricsListener(PostgresMetricsRecorder.kamon());
    }

    /**
     * @param recorder the metrics sink (a capturing fake in tests).
     * @return a listener recording into the given sink.
     */
    public static R2dbcMetricsListener of(final PostgresMetricsRecorder recorder) {
        return new R2dbcMetricsListener(recorder);
    }

    @Override
    public void afterQuery(final QueryExecutionInfo execInfo) {
        final String status = execInfo.isSuccess() && execInfo.getThrowable() == null
                ? PostgresMetrics.STATUS_SUCCESS
                : PostgresMetrics.STATUS_ERROR;
        final String opKind = PostgresMetrics.classifyOpKind(firstQuery(execInfo));
        final long nanos = execInfo.getExecuteDuration() == null ? 0L : execInfo.getExecuteDuration().toNanos();
        try {
            recorder.recordCommand(opKind, status, nanos);
        } catch (final RuntimeException e) {
            // Metrics must never break the query: swallow any recorder/registry failure (e.g. Kamon tag-cardinality
            // blow-up). Logged at DEBUG to avoid log spam under repeated registry failures.
            LOGGER.debug("Failed to record command metric (opKind={}, status={}): {}", opKind, status, e.getMessage(),
                    e);
        }
    }

    @Override
    public void afterCreateOnConnectionFactory(final MethodExecutionInfo methodExecutionInfo) {
        final long nanos = methodExecutionInfo.getExecuteDuration() == null
                ? 0L
                : methodExecutionInfo.getExecuteDuration().toNanos();
        try {
            recorder.recordAcquire(nanos);
        } catch (final RuntimeException e) {
            // Metrics must never break connection acquisition: swallow any recorder/registry failure. Logged at DEBUG
            // to avoid log spam under repeated registry failures.
            LOGGER.debug("Failed to record acquire metric (nanos={}): {}", nanos, e.getMessage(), e);
        }
    }

    private static String firstQuery(final QueryExecutionInfo execInfo) {
        final List<QueryInfo> queries = execInfo.getQueries();
        if (queries == null || queries.isEmpty()) {
            return null;
        }
        return queries.get(0).getQuery();
    }

}
