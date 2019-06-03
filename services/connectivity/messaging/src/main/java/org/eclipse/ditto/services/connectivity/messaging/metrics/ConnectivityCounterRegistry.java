/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.metrics;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_DAY;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_HOUR;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.MeasurementWindow.ONE_MINUTE;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionMetricsResponse;

/**
 * This registry holds counters for the connectivity service. The counters are identified by the connection id, a {@link
 * MetricType}, a {@link MetricDirection} and an address.
 */
public final class ConnectivityCounterRegistry {

    private static final ConcurrentMap<MapKey, ConnectionMetricsCollector> counters = new ConcurrentHashMap<>();

    private static final MeasurementWindow[] DEFAULT_WINDOWS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private static final Clock CLOCK_UTC = Clock.systemUTC();

    private ConnectivityCounterRegistry() {
        throw new AssertionError();
    }

    /**
     * Initializes the global {@code counters} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global counters map with.
     */
    public static void initCountersForConnection(final Connection connection) {

        final String connectionId = connection.getId();
        connection.getSources().stream()
                .map(Source::getAddresses)
                .flatMap(Collection::stream)
                .forEach(address ->
                        initCounter(connectionId, MetricDirection.INBOUND, address));
        connection.getTargets().stream()
                .map(Target::getAddress)
                .forEach(address ->
                        initCounter(connectionId, MetricDirection.OUTBOUND, address));
        initCounter(connectionId, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Resets the metric of the passed {@code connection} in the global {@code counters} Map + initializes it again.
     *
     * @param connection the connection to initialize the global counters map with.
     */
    public static void resetCountersForConnection(final Connection connection) {

        final String connectionId = connection.getId();
        counters.keySet().stream()
                .filter(key -> key.connectionId.equals(connectionId))
                .forEach(counters::remove);
    }

    private static void initCounter(final String connectionId, final MetricDirection metricDirection,
            final String address) {
        Arrays.stream(MetricType.values())
                .filter(metricType -> metricType.supportsDirection(metricDirection))
                .forEach(metricType -> {
                    final MapKey key = new MapKey(connectionId, metricType, metricDirection, address);
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

        final MapKey key = new MapKey(connectionId, metricType, metricDirection, address);
        return counters.computeIfAbsent(key, m -> {
            final SlidingWindowCounter counter = new SlidingWindowCounter(clock, DEFAULT_WINDOWS);
            return new ConnectionMetricsCollector(metricDirection, address, metricType, counter);
        });
    }

    /**
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#DISPATCHED} messages.
     *
     * @param connectionId connection id
     * @param target the target address
     * @return the counter
     */
    public static ConnectionMetricsCollector getOutboundDispatchedCounter(String connectionId, String target) {
        return getCounter(connectionId, MetricType.DISPATCHED, MetricDirection.OUTBOUND, target);
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
    public static ConnectionMetricsCollector getInboundConsumedCounter(String connectionId, String source) {
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
     * Gets counter for {@link MetricDirection#INBOUND}/{@link MetricType#ENFORCED} messages.
     *
     * @param connectionId connection id
     * @param source the source
     * @return the inbound enforced counter
     */
    public static ConnectionMetricsCollector getInboundEnforcedCounter(String connectionId, String source) {
        return getCounter(connectionId, MetricType.ENFORCED, MetricDirection.INBOUND, source);
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
     * Gets counter for {@link MetricDirection#OUTBOUND}/{@link MetricType#DISPATCHED} messages for responses.
     *
     * @param connectionId connection id
     * @return the response consumed counter
     */
    public static ConnectionMetricsCollector getResponseDispatchedCounter(String connectionId) {
        return getCounter(connectionId, MetricType.DISPATCHED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
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
                .filter(e -> e.getKey().connectionId.equals(connectionId))
                .filter(e -> metricDirection == e.getValue().getMetricDirection())
                .map(Map.Entry::getValue);
    }

    private static Map<String, AddressMetric> aggregateMetrics(final String connectionId,
            final MetricDirection metricDirection) {
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
     * Merges the passed in {@link RetrieveConnectionMetricsResponse}s into each other returning a new
     * {@link RetrieveConnectionMetricsResponse} containing the merged information.
     *
     * @param first the first RetrieveConnectionMetricsResponse to merge.
     * @param second the second RetrieveConnectionMetricsResponse to merge.
     * @return the new merged RetrieveConnectionMetricsResponse.
     */
    public static RetrieveConnectionMetricsResponse mergeRetrieveConnectionMetricsResponse(
            final RetrieveConnectionMetricsResponse first,
            final RetrieveConnectionMetricsResponse second) {

        final SourceMetrics mergedSourceMetrics = ConnectivityModelFactory.newSourceMetrics(
                mergeAddressMetricMap(
                        first.getSourceMetrics().getAddressMetrics(),
                        second.getSourceMetrics().getAddressMetrics())
        );

        final TargetMetrics mergedTargetMetrics = ConnectivityModelFactory.newTargetMetrics(mergeAddressMetricMap(
                first.getTargetMetrics().getAddressMetrics(),
                second.getTargetMetrics().getAddressMetrics())
        );

        final AddressMetric inboundMetrics = mergeAddressMetric(
                first.getConnectionMetrics().getInboundMetrics(),
                second.getConnectionMetrics().getInboundMetrics()
        );
        final AddressMetric outboundMetrics = mergeAddressMetric(
                first.getConnectionMetrics().getOutboundMetrics(),
                second.getConnectionMetrics().getOutboundMetrics()
        );

        final ConnectionMetrics mergedConnectionMetrics =
                ConnectivityModelFactory.newConnectionMetrics(inboundMetrics, outboundMetrics);

        return RetrieveConnectionMetricsResponse.getBuilder(first.getConnectionId(), first.getDittoHeaders())
                .connectionMetrics(mergedConnectionMetrics)
                .sourceMetrics(mergedSourceMetrics)
                .targetMetrics(mergedTargetMetrics)
                .build();
    }

    /**
     * Aggregates the passed in {@link SourceMetrics} and {@link TargetMetrics} into a new {@link ConnectionMetrics}
     * instance by merging them.
     *
     * @param sourceMetrics the SourceMetrics to include in the ConnectionMetrics.
     * @param targetMetrics the TargetMetrics to include in the ConnectionMetrics.
     * @return the combined new ConnectionMetrics.
     */
    public static ConnectionMetrics aggregateConnectionMetrics(
            final SourceMetrics sourceMetrics, final TargetMetrics targetMetrics) {
        final AddressMetric fromSources = mergeAllMetrics(sourceMetrics.getAddressMetrics().values());
        final AddressMetric fromTargets = mergeAllMetrics(targetMetrics.getAddressMetrics().values());
        return ConnectivityModelFactory.newConnectionMetrics(fromSources, fromTargets);
    }

    private static AddressMetric mergeAllMetrics(final Collection<AddressMetric> metrics) {
        AddressMetric result = ConnectivityModelFactory.emptyAddressMetric();
        for (AddressMetric metric : metrics) {
            result = mergeAddressMetric(result, metric);
        }
        return result;
    }

    /**
     * Merges the passed {@link AddressMetric}s and combines them into a new {@link AddressMetric} instance.
     *
     * @param first the first AddressMetric to merge
     * @param second the first AddressMetric to merge
     * @return the combined new AddressMetric.
     */
    public static AddressMetric mergeAddressMetric(final AddressMetric first, final AddressMetric second) {
        final Map<String, Measurement> mapA = asMap(first);
        final Map<String, Measurement> mapB = asMap(second);
        final Map<String, Measurement> result = new HashMap<>(mapA);
        mapB.forEach((keyFromA, measurementFromA) -> result.merge(keyFromA, measurementFromA,
                (measurementA, measurementB) -> {
                    final Map<Duration, Long> merged =
                            mergeMeasurements(measurementA.getCounts(), measurementB.getCounts());
                    return ConnectivityModelFactory.newMeasurement(measurementA.getMetricType(),
                            measurementA.isSuccess(),
                            merged, latest(
                                    measurementA.getLastMessageAt().orElse(null),
                                    measurementB.getLastMessageAt().orElse(null)
                            ));
                }));
        return ConnectivityModelFactory.newAddressMetric(new HashSet<>(result.values()));
    }

    /**
     * Merges the passed {@link AddressMetric} Maps and combines them into a new {@link AddressMetric} Map instance with
     * the address inside the key of the maps.
     *
     * @param first the first Map to merge
     * @param second the second Map to merge
     * @return the combined new AddressMetric map with address keys.
     */
    public static Map<String, AddressMetric> mergeAddressMetricMap(final Map<String, AddressMetric> first,
            final Map<String, AddressMetric> second) {
        final Map<String, AddressMetric> result = new HashMap<>(first);
        second.forEach((k, v) -> result.merge(k, v, ConnectivityCounterRegistry::mergeAddressMetric));
        return result;
    }

    private static Map<String, Measurement> asMap(final AddressMetric a) {
        return a.getMeasurements()
                .stream()
                .collect(Collectors.toMap(m -> m.getMetricType() + ":" + m.isSuccess(), m -> m));
    }

    private static Map<Duration, Long> mergeMeasurements(final Map<Duration, Long> measurementA,
            final Map<Duration, Long> measurementB) {
        final Map<Duration, Long> result = new HashMap<>(measurementA);
        measurementB.forEach((k, v) -> result.merge(k, v, Long::sum));
        return result;
    }

    @Nullable
    private static Instant latest(@Nullable final Instant instantA, @Nullable final Instant instantB) {
        if (instantA == null && instantB == null) {
            return null;
        } else if (instantA == null) {
            return instantB;
        } else if (instantB == null) {
            return instantA;
        } else {
            return Instant.ofEpochMilli(Math.max(instantA.toEpochMilli(), instantB.toEpochMilli()));
        }
    }

    /**
     * Helper class to build the map key of the registry.
     */
    @Immutable
    private static class MapKey {

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
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MapKey mapKey = (MapKey) o;
            return Objects.equals(connectionId, mapKey.connectionId) &&
                    Objects.equals(metric, mapKey.metric) &&
                    Objects.equals(direction, mapKey.direction) &&
                    Objects.equals(address, mapKey.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(connectionId, metric, direction, address);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    ", connectionId=" + connectionId +
                    ", metric=" + metric +
                    ", direction=" + direction +
                    ", address=" + address +
                    "]";
        }

    }
}
