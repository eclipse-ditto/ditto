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

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;

/**
 * This registry holds counters for the connectivity service. The counters are identified by the connection id, a
 * {@link Metric}, a {@link Direction} and an address.
 */
public final class ConnectivityCounterRegistry {

    private static final ConcurrentMap<String, ConnectionMetricsCollector> counters = new ConcurrentHashMap<>();

    private static final MeasurementWindow[] DEFAULT_WINDOWS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private static final Clock CLOCK_UTC = Clock.systemUTC();

    /**
     * Gets the counter for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param connectionId connection id
     * @param metric the metric
     * @param direction the direction
     * @param address the address
     * @return the counter
     */
    public static ConnectionMetricsCollector getCounter(
            final String connectionId,
            final Metric metric,
            final Direction direction,
            final String address) {


        return getCounter(CLOCK_UTC, connectionId, metric, direction, address);
    }

    /**
     * Gets the counter for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param clock custom clock (only used for testing)
     * @param connectionId connection id
     * @param metric counter metric
     * @param direction counter direction
     * @param address address e.g. source or target address
     * @return the counter
     */
    static ConnectionMetricsCollector getCounter(
            final Clock clock,
            final String connectionId,
            final Metric metric,
            final Direction direction,
            final String address) {

        final String key = new MapKey(connectionId, metric, direction, address).toString();
        return counters.computeIfAbsent(key, m -> {
            final SlidingWindowCounter counter = new SlidingWindowCounter(clock, DEFAULT_WINDOWS);
            return new ConnectionMetricsCollector(direction, address, metric, counter);
        });
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#CONSUMED} messages.
     *
     * @param connectionId connection id
     * @param target the target address
     * @return the counter
     */
    public static ConnectionMetricsCollector getOutboundCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.CONSUMED, Direction.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#FILTERED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound filtered counter
     */
    public static ConnectionMetricsCollector getOutboundFilteredCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.FILTERED, Direction.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#MAPPED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound mapped counter
     */
    public static ConnectionMetricsCollector getOutboundMappedCounter(String connectionId, final String target) {
        return getCounter(connectionId, Metric.MAPPED, Direction.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#PUBLISHED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound published counter
     */
    public static ConnectionMetricsCollector getOutboundPublishedCounter(String connectionId, String target) {
        return getCounter(connectionId, Metric.PUBLISHED, Direction.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link Direction#INBOUND}/{@link Metric#CONSUMED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound counter
     */
    public static ConnectionMetricsCollector getInboundCounter(String connectionId, String source) {
        return getCounter(connectionId, Metric.CONSUMED, Direction.INBOUND, source);
    }

    /**
     * Gets counter for {@link Direction#INBOUND}/{@link Metric#MAPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound mapped counter
     */
    public static ConnectionMetricsCollector getInboundMappedCounter(String connectionId, String source) {
        return getCounter(connectionId, Metric.MAPPED, Direction.INBOUND, source);
    }

    /**
     * Gets counter for {@link Direction#INBOUND}/{@link Metric#DROPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound dropped counter
     */
    public static ConnectionMetricsCollector getInboundDroppedCounter(String connectionId, String source) {
        return getCounter(connectionId, Metric.DROPPED, Direction.INBOUND, source);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#CONSUMED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response consumed counter
     */
    public static ConnectionMetricsCollector getResponseConsumedCounter(String connectionId) {
        return getCounter(connectionId, Metric.CONSUMED, Direction.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#DROPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response dropped counter
     */
    public static ConnectionMetricsCollector getResponseDroppedCounter(String connectionId) {
        return getCounter(connectionId, Metric.DROPPED, Direction.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#MAPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response mapped counter
     */
    public static ConnectionMetricsCollector getResponseMappedCounter(String connectionId) {
        return getCounter(connectionId, Metric.MAPPED, Direction.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link Direction#OUTBOUND}/{@link Metric#PUBLISHED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response published counter
     */
    public static ConnectionMetricsCollector getResponsePublishedCounter(String connectionId) {
        return getCounter(connectionId, Metric.PUBLISHED, Direction.OUTBOUND, RESPONSES_ADDRESS);
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
                            swc.toMeasurement(true).ifPresent(measurements::add);
                            swc.toMeasurement(false).ifPresent(measurements::add);
                            return metric != null
                                    ? ConnectivityModelFactory.newAddressMetric(metric, measurements)
                                    : ConnectivityModelFactory.newAddressMetric(measurements);
                        }));
        return addressMetrics;
    }

    /**
     * Aggregate the {@link SourceMetrics} for the given connection from the counters in this registry.
     *
     * @param connectionId connection id
     * @return the {@link SourceMetrics}
     */
    public static SourceMetrics aggregateSourceMetrics(final String connectionId) {
        return ConnectivityModelFactory.newSourceMetrics(aggregateMetrics(connectionId, Direction.INBOUND));
    }

    /**
     * Aggregate the {@link TargetMetrics} for the given connection from the counters in this registry.
     *
     * @param connectionId connection id
     * @return the {@link TargetMetrics}
     */
    public static TargetMetrics aggregateTargetMetrics(final String connectionId) {
        return ConnectivityModelFactory.newTargetMetrics(aggregateMetrics(connectionId, Direction.OUTBOUND));
    }

    /**
     * Defines known counter metrics.
     */
    public enum Metric {

        /**
         * Counts mappings for messages.
         */
        MAPPED("mapped"),

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
        PUBLISHED("published"),

        /**
         * Counts messages that were dropped (not published by intention e.g. because no reply-to address was given).
         */
        DROPPED("dropped");

        private final String label;

        Metric(final String label) {
            this.label = label;
        }

        /**
         * @return the label which can be used in a JSON representation
         */
        public String getLabel() {
            return label;
        }
    }

    /**
     * Defines the direction of a counter e.g. if inbound or outbound messages are counted.
     */
    public enum Direction {

        /**
         * Inbound direction from external systems to Ditto.
         */
        INBOUND("inbound"),

        /**
         * Outbound direction from Ditto to external systems.
         */
        OUTBOUND("outbound");

        private final String label;

        Direction(final String label) {
            this.label = label;
        }

        private String getLabel() {
            return label;
        }
    }

    /**
     * Helper class to build the map key of the registry.
     */
    private static class MapKey implements CharSequence {

        private final String connectionId;
        private final String metric;
        private final String direction;
        private final String address;

        /**
         * New map key.
         *
         * @param connectionId connection id
         * @param metric the metric
         * @param direction the direction
         * @param address the address
         */
        MapKey(final String connectionId,
                final Metric metric,
                final Direction direction,
                final String address) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            this.metric = metric.getLabel();
            this.direction = direction.getLabel();
            this.address = checkNotNull(address, "address");
        }

        @Override
        public int length() {
            return toString().length();
        }

        @Override
        public char charAt(final int index) {
            return toString().charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return toString().subSequence(start, end);
        }

        @Override
        @Nonnull
        public String toString() {
            return connectionId
                    + ":" + metric
                    + ":" + direction
                    + ":" + address;
        }
    }
}
