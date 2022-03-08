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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.service.config.ConnectionThrottlingConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.Gauge;
import org.eclipse.ditto.internal.utils.metrics.instruments.gauge.KamonGauge;

import akka.japi.Pair;

/**
 * Registry to keep track and update existing {@code MetricsAlerts}.
 */
public final class MetricAlertRegistry {

    public static final String COUNTER_ACK_HANDLING = "connectivity_ack_handling_counter";

    /**
     * Defines which measurement window is used to detect throttling i.e. what is the maximum allowed messages per
     * interval. The throttling limits from ConnectivityConfig must be adjusted to the resolution of this window
     * (see {@link #calculateThrottlingLimitFromConfig}).
     */
    private static final MeasurementWindow THROTTLING_DETECTION_WINDOW =
            MeasurementWindow.ONE_MINUTE_WITH_TEN_SECONDS_RESOLUTION;

    /**
     * An alert can be registered for a combination of MetricType and MetricDirection e.g. CONSUMED + INBOUND.
     * These alerts will be instantiated using the registered Creator and passed to created SlidingWindowCounters.
     */
    private static final Map<Pair<ConnectionType, MetricsAlert.Key>, MetricsAlertFactory> ALERT_DEFINITIONS = Map.of(
            Pair.apply(ConnectionType.AMQP_10, MetricsAlert.Key.CONSUMED_INBOUND), getThrottledAlert(),
            Pair.apply(ConnectionType.KAFKA, MetricsAlert.Key.MAPPED_INBOUND), getThrottledAlert());

    private static final Map<Pair<ConnectionType, String>, MetricsAlertFactory> GAUGE_ALERT_DEFINITIONS = Map.of(
            Pair.apply(ConnectionType.AMQP_10, COUNTER_ACK_HANDLING), getThrottledAlert(),
            Pair.apply(ConnectionType.KAFKA, COUNTER_ACK_HANDLING), getThrottledAlert());

    private static final ConcurrentMap<Pair<ConnectionType, CounterKey>, MetricsAlert> ALERTS =
            new ConcurrentHashMap<>();

    private final Map<Pair<ConnectionType, MetricsAlert.Key>, MetricsAlertFactory> customAlerts = new HashMap<>();

    private static MetricsAlertFactory getThrottledAlert() {
        return (source, connectionType, config, isGauge) -> {
            // target counter is INBOUND + THROTTLED
            final CounterKey target = CounterKey.of(source.getConnectionId(), source.getAddress(), MetricType.THROTTLED,
                    MetricDirection.INBOUND);

            return new ThrottledMetricsAlert(THROTTLING_DETECTION_WINDOW,
                    calculateThrottlingLimitFromConfig(connectionType, config, isGauge),
                    () -> ConnectivityCounterRegistry.lookup(target));
        };
    }

    /**
     * Registers an alert with a custom MetricsAlertFactory.
     *
     * @param connectionType the type of the connection the alert is applicable for.
     * @param key the alert key
     * @param metricsAlertFactory the factory used to instantiate the alert
     */
    void registerCustomAlert(final ConnectionType connectionType, final MetricsAlert.Key key,
            final MetricsAlertFactory metricsAlertFactory) {

        customAlerts.put(Pair.apply(connectionType, key), metricsAlertFactory);
    }

    @Nullable
    MetricsAlert getAlert(final CounterKey counterKey, final ConnectionType connectionType,
            final ConnectivityConfig connectivityConfig) {

        checkNotNull(counterKey.getMetricDirection(), "metricDirection");
        checkNotNull(counterKey.getMetricType(), "metricType");
        return Optional.ofNullable(ALERTS.get(Pair.apply(connectionType, counterKey)))
                .or(() -> MetricsAlert.Key.from(counterKey.getMetricDirection().get(), counterKey.getMetricType().get())
                        .map(key -> Optional.ofNullable(ALERT_DEFINITIONS.get(Pair.apply(connectionType, key)))
                                .orElse(customAlerts.get(Pair.apply(connectionType, key))))
                        .map(creator -> ALERTS.computeIfAbsent(Pair.apply(connectionType, counterKey),
                                mk -> creator.create(counterKey, connectionType, connectivityConfig, false))))
                .orElse(null);
    }


    public static Gauge getMetricsAlertGaugeOrDefault(final CounterKey counterKey,
            final String gaugeName,
            final ConnectionType connectionType,
            final ConnectivityConfig connectivityConfig) {

        return Optional.ofNullable(GAUGE_ALERT_DEFINITIONS.get(Pair.apply(connectionType, gaugeName)))
                .map(alertFactory -> alertFactory.create(counterKey, connectionType, connectivityConfig, true))
                .map(alert -> MetricsAlertGauge.newGauge(gaugeName, alert, THROTTLING_DETECTION_WINDOW))
                .orElse(KamonGauge.newGauge(gaugeName));
    }

    private static long calculateThrottlingLimitFromConfig(final ConnectionType connectionType,
            final ConnectivityConfig config, final boolean isGauge) {

        switch (connectionType) {
            case AMQP_10:
                final ConnectionThrottlingConfig amqp10ThrottlingConfig =
                        config.getConnectionConfig().getAmqp10Config().getConsumerConfig().getThrottlingConfig();
                return perInterval(amqp10ThrottlingConfig, THROTTLING_DETECTION_WINDOW.getResolution(), isGauge);
            case KAFKA:
                final ConnectionThrottlingConfig kafkaThrottlingConfig =
                        config.getConnectionConfig().getKafkaConfig().getConsumerConfig().getThrottlingConfig();
                return perInterval(kafkaThrottlingConfig, THROTTLING_DETECTION_WINDOW.getResolution(), isGauge);
            case AMQP_091:
            case HTTP_PUSH:
            case MQTT:
            case MQTT_5:
            default:
                // effectively no limit
                return Integer.MAX_VALUE;
        }
    }

    private static long perInterval(final ConnectionThrottlingConfig throttlingConfig, final Duration resolution,
            final boolean isGauge) {

        final long result;
        if (isGauge) {
            result = perIntervalForGauge(throttlingConfig);
        } else {
            result = perIntervalForConnectionMetric(throttlingConfig, resolution);
        }
        return result;
    }

    private static long perIntervalForConnectionMetric(final ConnectionThrottlingConfig throttlingConfig, final Duration resolution) {
        final double tolerance = throttlingConfig.getThrottlingDetectionTolerance();
        final Duration interval = throttlingConfig.getInterval();
        // calculate factor to adjust the limit to the given resolution
        final double factor = (double) resolution.toMillis() / interval.toMillis();
        final int limit = throttlingConfig.getLimit();
        final double limitAdjustedToResolution = limit * factor;

        // apply the configured tolerance to the resulting limit
        return (long) (limitAdjustedToResolution * (1 - tolerance));
    }

    private static long perIntervalForGauge(final ConnectionThrottlingConfig throttlingConfig) {
        // Return plain max inflight, since this is the single threshold determining in-flight throttling without
        // influence of time window.
        final double tolerance = throttlingConfig.getThrottlingDetectionTolerance();
        return (long) (throttlingConfig.getMaxInFlight() * (1 - tolerance));
    }

}
