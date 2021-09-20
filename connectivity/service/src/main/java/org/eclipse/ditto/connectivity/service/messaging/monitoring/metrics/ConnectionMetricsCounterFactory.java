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
import java.time.Duration;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.counter.Counter;

/**
 * Factory class for
 * {@link org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectionMetricsCounter}s.
 */
final class ConnectionMetricsCounterFactory {

    private static final MeasurementWindow[] DEFAULT_WINDOWS =
            {MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION,
                    MeasurementWindow.ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
                    MeasurementWindow.ONE_DAY_WITH_ONE_HOUR_RESOLUTION};

    /**
     * Defines which measurement window is used to detect throttling i.e. what is the maximum allowed messages per
     * interval. The throttling limits from ConnectivityConfig must be adjusted to the resolution of this window
     * (see {@link #calculateThrottlingLimitFromConfig}).
     */
    private static final MeasurementWindow THROTTLING_DETECTION_WINDOW =
            MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION;

    /**
     * An alert can be registered for a combination of MetricType and MetricDirection e.g. CONSUMED + INBOUND. These
     * alerts will be instantiated using the registered Creator and used with newly created SlidingWindowCounters.
     */
    private static final Map<MetricsAlert.Key, AlertsCreator> alerts = Map.of(
            MetricsAlert.Key.CONSUMED_INBOUND,
            (connectionId, connectionType, address, config) -> {
                final ConnectivityCounterRegistry.MapKey
                        target = new ConnectivityCounterRegistry.MapKey(connectionId, MetricType.THROTTLED,
                        MetricDirection.INBOUND, address);
                return new ThrottledMetricsAlert(THROTTLING_DETECTION_WINDOW,
                        calculateThrottlingLimitFromConfig(connectionType, config),
                        () -> ConnectivityCounterRegistry.lookup(target));
            }
    );

    private ConnectionMetricsCounterFactory() {}

    static DefaultConnectionMetricsCounter create(
            final MetricType metricType,
            final MetricDirection metricDirection, final ConnectionId connectionId,
            final ConnectionType connectionType,
            final String address,
            final Clock clock, final ConnectivityConfig connectivityConfig) {
        final Counter metricsCounter = metricsCounter(connectionId, connectionType, metricType, metricDirection);
        final SlidingWindowCounter counter;
        if (MetricType.THROTTLED == metricType) {
            counter = SlidingWindowCounter.newBuilder(metricsCounter).clock(clock)
                    // we need to record for every minute of the last 24h if throttling occurred
                    .recordingMeasurementWindows(MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                    // reporting windows are the same as for the other metrics (1m, 1h, 1d)
                    .reportingMeasurementWindows(MeasurementWindow.ONE_MINUTE_WITH_ONE_MINUTE_RESOLUTION,
                            MeasurementWindow.ONE_HOUR_WITH_ONE_MINUTE_RESOLUTION,
                            MeasurementWindow.ONE_DAY_WITH_ONE_MINUTE_RESOLUTION)
                    .maximumPerSlot(1L)
                    .build();
        } else {
            final MetricsAlert metricsAlert = resolveOptionalAlert(metricDirection, address, metricType, connectionId,
                    connectionType, connectivityConfig);
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

    @Nullable
    private static MetricsAlert resolveOptionalAlert(final MetricDirection metricDirection, final String address,
            final MetricType metricType, final ConnectionId connectionId, final ConnectionType connectionType,
            final ConnectivityConfig connectivityConfig) {
        return MetricsAlert.Key.from(metricDirection, metricType)
                .map(alerts::get)
                .map(c -> c.create(connectionId, connectionType, address, connectivityConfig))
                .orElse(null);
    }

    private static int calculateThrottlingLimitFromConfig(final ConnectionType connectionType,
            final ConnectivityConfig config) {
        switch (connectionType) {
            case AMQP_10:
                final ConnectionThrottlingConfig amqp10ThrottlingConfig =
                        config.getConnectionConfig().getAmqp10Config().getConsumerConfig().getThrottlingConfig();
                return perInterval(amqp10ThrottlingConfig, THROTTLING_DETECTION_WINDOW.getResolution());
            case KAFKA:
                final ConnectionThrottlingConfig kafkaThrottlingConfig =
                        config.getConnectionConfig().getKafkaConfig().getConsumerConfig().getThrottlingConfig();
                return perInterval(kafkaThrottlingConfig, THROTTLING_DETECTION_WINDOW.getResolution());
            case MQTT:
            case AMQP_091:
            case HTTP_PUSH:
            case MQTT_5:
            default:
                // effectively no limit
                return Integer.MAX_VALUE;
        }
    }

    private static int perInterval(final ThrottlingConfig throttlingConfig, final Duration resolution) {
        final Duration interval = throttlingConfig.getInterval();
        final float factor = (float) resolution.toMillis() / interval.toMillis();
        final int limit = throttlingConfig.getLimit();
        return Math.round(limit * factor);
    }

    /**
     * Creator interface for MetricsAlerts which are stored in the map of existing {@link #alerts}.
     */
    @FunctionalInterface
    interface AlertsCreator {

        /**
         * Create a new instantiation of a metrics alert.
         *
         * @param connectionId the connection id
         * @param connectionType the connection type
         * @param address the address
         * @param connectivityConfig the connectivity config
         * @return the new metrics alert
         */
        MetricsAlert create(final ConnectionId connectionId, final ConnectionType connectionType, final String address,
                final ConnectivityConfig connectivityConfig);
    }
}
