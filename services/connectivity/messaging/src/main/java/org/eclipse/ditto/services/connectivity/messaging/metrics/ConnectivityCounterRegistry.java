/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_DAY;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_HOUR;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_MINUTE;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;

public class ConnectivityCounterRegistry {

    private static final ConcurrentMap<String, ConnectionMetricsCollector> counters = new ConcurrentHashMap<>();

    private static final MeasurementWindow[] DEFAULT_WINDOWS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

    static ConnectionMetricsCollector getCounter(
            final String connectionId,
            final Metric metric,
            final Direction direction,
            final String address) {

        return getCounter(Clock.systemUTC(), connectionId, metric, direction, address);
    }

    static ConnectionMetricsCollector getCounter(
            final Clock clock,
            final String connectionId,
            final Metric metric,
            final Direction direction,
            final String address) {

        final String key =
                MapKeyBuilder.newBuilder(connectionId, metric, direction).address(address).build();
        return counters.computeIfAbsent(key, m -> {
            final SlidingWindowCounter counter = new SlidingWindowCounter(clock, DEFAULT_WINDOWS);
            return new ConnectionMetricsCollector(direction, address, metric, counter);
        });
    }

    public static ConnectionMetricsCollector getOutboundCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.CONSUMED, Direction.OUTBOUND, target);
    }

    public static ConnectionMetricsCollector getOutboundFilteredCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.FILTERED, Direction.OUTBOUND, target);
    }

    public static ConnectionMetricsCollector getOutboundMappedCounter(String connectionId, final String target) {
        return getCounter(connectionId, Metric.MAPPED, Direction.OUTBOUND, target);
    }

    public static ConnectionMetricsCollector getOutboundPublishedCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.PUBLISHED, Direction.OUTBOUND, target);
    }

    public static ConnectionMetricsCollector getInboundCounter(String connectionId, String source) {
        return getCounter(connectionId, Metric.CONSUMED, Direction.INBOUND, source);
    }

    public static ConnectionMetricsCollector getInboundMappedCounter(String connectionId, String source) {
        return getCounter(connectionId, Metric.MAPPED, Direction.INBOUND, source);
    }

    public static ConnectionMetricsCollector getRespondedCounter(String connectionId) {
        return getCounter(connectionId, Metric.RESPONDED, Direction.INBOUND, "reply");
    }

    private static Stream<ConnectionMetricsCollector> streamFor(final String connectionId,
            final ConnectivityCounterRegistry.Direction direction) {

        return counters.entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(connectionId))
                .filter(e -> direction == e.getValue().getDirection())
                .map(Map.Entry::getValue);
    }

    private static Map<String, AddressMetric> aggregateMetrics(final String connectionId, final Direction direction) {
        final Map<String, AddressMetric> addressMetrics = new HashMap<>();
        streamFor(connectionId, direction)
                .forEach(swc -> addressMetrics.compute(swc.getAddress(),
                        (address, metric) -> {

                            final Set<Measurement> measurements = new HashSet<>();
                            measurements.add(swc.toMeasurement(true));
                            measurements.add(swc.toMeasurement(false));

                            return metric != null
                                    ? ConnectivityModelFactory.newAddressMetric(metric, measurements)
                                    : ConnectivityModelFactory.newAddressMetric(measurements);
                        }));

        return addressMetrics;
    }

    public static SourceMetrics aggregateSourceMetrics(final String connectionId) {
        return ConnectivityModelFactory.newSourceMetrics(aggregateMetrics(connectionId, Direction.INBOUND));
    }

    public static TargetMetrics aggregateTargetMetrics(final String connectionId) {
        return ConnectivityModelFactory.newTargetMetrics(aggregateMetrics(connectionId, Direction.OUTBOUND));
    }

    public enum Metric {

        /**
         * Counts mappings for messages.
         */
        MAPPED("mapped"),

        /**
         * Counts messages that are responses to received commands.
         */
        RESPONDED("responded"),

        /**
         * Counts messages that were consumed.
         */
        CONSUMED("consumed"),

        /**
         * Counts messages to external systems that passed the configured filter.
         */
        FILTERED("filtered"),

        /**
         * Counts messages published to external systems.
         */
        PUBLISHED("published");

        private final String label;

        Metric(final String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Direction {

        INBOUND("inbound"),

        OUTBOUND("outbound");

        private final String label;

        Direction(final String label) {
            this.label = label;
        }

        private String getLabel() {
            return label;
        }
    }

    static class MapKeyBuilder {

        final String connectionId;
        final String metric;
        final String direction;
        String address;

        private MapKeyBuilder(final String connectionId,
                final String metric,
                final String direction) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            this.metric = metric;
            this.direction = direction;
        }

        static MapKeyBuilder newBuilder(final String connectionId, final Metric metric,
                final Direction direction) {
            return new MapKeyBuilder(connectionId, metric.getLabel(), direction.getLabel());
        }

        MapKeyBuilder address(final String address) {
            this.address = address;
            return this;
        }

        String build() {
            return connectionId
                    + ":" + metric
                    + ":" + direction
                    + (address != null ? ":" + address : "");
        }

    }
}
