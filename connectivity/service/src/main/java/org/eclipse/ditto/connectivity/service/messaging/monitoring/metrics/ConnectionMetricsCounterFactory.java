/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.time.Clock;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;

/**
 * Factory class for
 * {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter}s.
 */
final class ConnectionMetricsCounterFactory {

    private static final MeasurementWindow[] DEFAULT_WINDOWS = {
            MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION,
            MeasurementWindow.ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
            MeasurementWindow.ONE_DAY_WITH_ONE_HOUR_RESOLUTION
    };

    /**
     * Create a new instance of a DefaultConnectionMetricsCounter from the given arguments.
     *
     * @param metricType the metric type
     * @param metricDirection the metric direction
     * @param connectionId the connection id required to build a metrics counter
     * @param connectionType the connection type required to build a metrics counter
     * @param address the monitored address
     * @param clock the clock to be used
     * @param metricsAlert the metricsAlert
     */
    static DefaultConnectionMetricsCounter create(
            final MetricType metricType,
            final MetricDirection metricDirection,
            final ConnectionId connectionId,
            final ConnectionType connectionType,
            final String address,
            final Clock clock,
            @Nullable final MetricsAlert metricsAlert) {
        final Counter metricsCounter = metricsCounter(connectionId, connectionType, metricType, metricDirection);
        final SlidingWindowCounter counter;
        if (MetricType.THROTTLED == metricType) {
            counter = SlidingWindowCounter.newBuilder(metricsCounter)
                    .clock(clock)
                    // we need to record for every minute of the last 24h if throttling occurred
                    .recordingMeasurementWindows(MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                    // reporting windows are the same as for the other metrics (1m, 1h, 1d)
                    .reportingMeasurementWindows(MeasurementWindow.ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION,
                            MeasurementWindow.ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
                            MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                    .maximumPerSlot(1L)
                    .useLastTimestampForWindow(MeasurementWindow.ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION, 1L)
                    .metricsAlert(metricsAlert)
                    .build();
        } else {
            counter = SlidingWindowCounter.newBuilder(metricsCounter)
                    .clock(clock)
                    .metricsAlert(metricsAlert)
                    .measurementWindows(DEFAULT_WINDOWS)
                    .build();
        }

        return new DefaultConnectionMetricsCounter(metricDirection, address, metricType, counter);
    }

    private static Counter metricsCounter(final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MetricType metricType,
            final MetricDirection metricDirection) {

        return DittoMetrics.counter("connection_messages")
                .tag("id", connectionId.toString())
                .tag("type", connectionType.getName())
                .tag("category", metricType.getName())
                .tag("direction", metricDirection.getName());
    }

    private ConnectionMetricsCounterFactory() {}

}
