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

import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;

/**
 * Sink for the {@link PostgresMetrics} family. The default implementation records into Kamon via {@link DittoMetrics};
 * tests substitute a capturing fake so the listener can be asserted without a metrics backend.
 */
public interface PostgresMetricsRecorder {

    /**
     * Records a command-duration sample.
     *
     * @param opKind the neutral op-kind (one of {@link PostgresMetrics#OP_KIND_DOMAIN}).
     * @param status the status ({@link PostgresMetrics#STATUS_SUCCESS} / {@link PostgresMetrics#STATUS_ERROR}).
     * @param nanos the elapsed time in nanoseconds.
     */
    void recordCommand(String opKind, String status, long nanos);

    /**
     * Records a connection-acquire latency sample.
     *
     * @param nanos the elapsed time in nanoseconds.
     */
    void recordAcquire(long nanos);

    /**
     * @return the default Kamon-backed recorder.
     */
    static PostgresMetricsRecorder kamon() {
        return new PostgresMetricsRecorder() {
            @Override
            public void recordCommand(final String opKind, final String status, final long nanos) {
                DittoMetrics.timer(PostgresMetrics.COMMAND_DURATION)
                        .tag(PostgresMetrics.TAG_ENGINE, PostgresMetrics.ENGINE_POSTGRES)
                        .tag(PostgresMetrics.TAG_OP_KIND, opKind)
                        .tag(PostgresMetrics.TAG_STATUS, status)
                        .record(nanos, TimeUnit.NANOSECONDS);
            }

            @Override
            public void recordAcquire(final long nanos) {
                DittoMetrics.timer(PostgresMetrics.ACQUIRE_DURATION)
                        .tag(PostgresMetrics.TAG_ENGINE, PostgresMetrics.ENGINE_POSTGRES)
                        .record(nanos, TimeUnit.NANOSECONDS);
            }
        };
    }

}
