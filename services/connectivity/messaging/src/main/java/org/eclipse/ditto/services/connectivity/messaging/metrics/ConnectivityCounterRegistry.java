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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;

/**
 * This registry holds counters for the connectivity service. The counters are identified by the connection id, a
 * {@link MetricType}, a {@link MetricDirection} and an address.
 */
public final class ConnectivityCounterRegistry {

    private static final ConcurrentMap<String, ConnectionMetricsCollector> counters = new ConcurrentHashMap<>();

    private static final MeasurementWindow[] DEFAULT_WINDOWS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private static final Clock CLOCK_UTC = Clock.systemUTC();

    /**
     * Initializes the global {@code counters} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global counters map with.
     */
    public static void initCountersForConnection(final Connection connection) {

        final String connectionId = connection.getId();
        connection.getSources().stream()
                .map(Source::getAddresses)
                .forEach(addresses -> addresses
                        .forEach(address ->
                                initCounter(connectionId, MetricDirection.INBOUND, address)));
        connection.getTargets().stream()
                .map(Target::getAddress)
                .forEach(address ->
                        initCounter(connectionId, MetricDirection.OUTBOUND, address));
    }

    private static void initCounter(final String connectionId, final MetricDirection metricDirection, final String address) {
        Arrays.stream(MetricType.values())
                .filter(metricType -> metricType.getPossibleMetricDirections().contains(metricDirection))
                .forEach(metricType -> {
                    final String key = new MapKey(connectionId, metricType, metricDirection, address).toString();
                    counters.computeIfAbsent(key, m -> {
                        final SlidingWindowCounter counter = new SlidingWindowCounter(CLOCK_UTC, DEFAULT_WINDOWS);
                        return new ConnectionMetricsCollector(metricDirection, address, metricType, counter);
                    });
                });
    }

    /**
     * Gets the counter for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param connectionId connection id
     * @param metricType the metricType
     * @param metricDirection the metricDirection
     * @param address the address
     * @return the counter
     */
    public static ConnectionMetricsCollector getCounter(
            final String connectionId,
            final MetricType metricType,
            final MetricDirection metricDirection,
            final String address) {

        return getCounter(CLOCK_UTC, connectionId, metricType, metricDirection, address);
    }

    /**
     * Gets the counter for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param clock custom clock (only used for testing)
     * @param connectionId connection id
     * @param metricType counter metricType
     * @param metricDirection counter metricDirection
     * @param address address e.g. source or target address
     * @return the counter
     */
    static ConnectionMetricsCollector getCounter(
            final Clock clock,
            final String connectionId,
            final MetricType metricType,
            final MetricDirection metricDirection,
            final String address) {

        final String key = new MapKey(connectionId, metricType, metricDirection, address).toString();
        return counters.computeIfAbsent(key, m -> {
            final SlidingWindowCounter counter = new SlidingWindowCounter(clock, DEFAULT_WINDOWS);
            return new ConnectionMetricsCollector(metricDirection, address, metricType, counter);
        });
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#CONSUMED} messages.
     *
     * @param connectionId connection id
     * @param target the target address
     * @return the counter
     */
    public static ConnectionMetricsCollector getOutboundCounter(String connectionId, String target) {
        return getCounter(connectionId, MetricType.CONSUMED, MetricDirection.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#FILTERED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound filtered counter
     */
    public static ConnectionMetricsCollector getOutboundFilteredCounter(String connectionId, String target) {
        return getCounter(connectionId, MetricType.FILTERED, MetricDirection.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#MAPPED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound mapped counter
     */
    public static ConnectionMetricsCollector getOutboundMappedCounter(String connectionId, final String target) {
        return getCounter(connectionId, MetricType.MAPPED, MetricDirection.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#PUBLISHED} messages.
     *
     * @param connectionId connection id
     * @param target the target
     * @return the outbound published counter
     */
    public static ConnectionMetricsCollector getOutboundPublishedCounter(String connectionId, String target) {
        return getCounter(connectionId, MetricType.PUBLISHED, MetricDirection.OUTBOUND, target);
    }

    /**
     * Gets counter for {@link MetricDirection#INBOUND}/{@link MetricType#CONSUMED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound counter
     */
    public static ConnectionMetricsCollector getInboundCounter(String connectionId, String source) {
        return getCounter(connectionId, MetricType.CONSUMED, MetricDirection.INBOUND, source);
    }

    /**
     * Gets counter for {@link MetricDirection#INBOUND}/{@link MetricType#MAPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound mapped counter
     */
    public static ConnectionMetricsCollector getInboundMappedCounter(String connectionId, String source) {
        return getCounter(connectionId, MetricType.MAPPED, MetricDirection.INBOUND, source);
    }

    /**
     * Gets counter for {@link MetricDirection#INBOUND}/{@link MetricType#DROPPED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound dropped counter
     */
    public static ConnectionMetricsCollector getInboundDroppedCounter(String connectionId, String source) {
        return getCounter(connectionId, MetricType.DROPPED, MetricDirection.INBOUND, source);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#CONSUMED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response consumed counter
     */
    public static ConnectionMetricsCollector getResponseConsumedCounter(String connectionId) {
        return getCounter(connectionId, MetricType.CONSUMED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#DROPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response dropped counter
     */
    public static ConnectionMetricsCollector getResponseDroppedCounter(String connectionId) {
        return getCounter(connectionId, MetricType.DROPPED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#MAPPED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response mapped counter
     */
    public static ConnectionMetricsCollector getResponseMappedCounter(String connectionId) {
        return getCounter(connectionId, MetricType.MAPPED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#PUBLISHED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response published counter
     */
    public static ConnectionMetricsCollector getResponsePublishedCounter(String connectionId) {
        return getCounter(connectionId, MetricType.PUBLISHED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    private static Stream<ConnectionMetricsCollector> streamFor(final String connectionId,
            final MetricDirection metricDirection) {

        return counters.entrySet()
                .stream()
                .filter(e -> e.getKey().startsWith(connectionId))
                .filter(e -> metricDirection == e.getValue().getMetricDirection())
                .map(Map.Entry::getValue);
    }

    private static Map<String, AddressMetric> aggregateMetrics(final String connectionId, final MetricDirection metricDirection) {
        final Map<String, AddressMetric> addressMetrics = new HashMap<>();
        streamFor(connectionId, metricDirection)
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

    /**
     * Aggregate the {@link SourceMetrics} for the given connection from the counters in this registry.
     *
     * @param connectionId connection id
     * @return the {@link SourceMetrics}
     */
    public static SourceMetrics aggregateSourceMetrics(final String connectionId) {
        return ConnectivityModelFactory.newSourceMetrics(aggregateMetrics(connectionId, MetricDirection.INBOUND));
    }

    /**
     * Aggregate the {@link TargetMetrics} for the given connection from the counters in this registry.
     *
     * @param connectionId connection id
     * @return the {@link TargetMetrics}
     */
    public static TargetMetrics aggregateTargetMetrics(final String connectionId) {
        return ConnectivityModelFactory.newTargetMetrics(aggregateMetrics(connectionId, MetricDirection.OUTBOUND));
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
         * @param metricType the metricType
         * @param metricDirection the metricDirection
         * @param address the address
         */
        MapKey(final String connectionId,
                final MetricType metricType,
                final MetricDirection metricDirection,
                final String address) {
            this.connectionId = checkNotNull(connectionId, "connectionId");
            this.metric = metricType.getName();
            this.direction = metricDirection.getName();
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
