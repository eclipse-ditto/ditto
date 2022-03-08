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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.AddressMetric;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionMetrics;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.Measurement;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.SourceMetrics;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.TargetMetrics;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionMetricsResponse;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitorRegistry;

/**
 * This registry holds counters for the connectivity service. The counters are identified by the connection id,
 * a {@link MetricType}, a {@link MetricDirection} and an address.
 */
public final class ConnectivityCounterRegistry implements ConnectionMonitorRegistry<ConnectionMetricsCounter> {

    private static final ConcurrentMap<CounterKey, DefaultConnectionMetricsCounter> counters =
            new ConcurrentHashMap<>();
    private static final MetricAlertRegistry alertRegistry = new MetricAlertRegistry();

    // artificial internal address for responses
    private static final String RESPONSES_ADDRESS = "_responses";

    private static final Clock CLOCK_UTC = Clock.systemUTC();

    private final ConnectivityConfig connectivityConfig;

    private ConnectivityCounterRegistry(final ConnectivityConfig connectivityConfig) {
        this.connectivityConfig = connectivityConfig;
    }

    public static ConnectivityCounterRegistry newInstance(final ConnectivityConfig connectivityConfig) {
        return new ConnectivityCounterRegistry(connectivityConfig);
    }

    static ConnectionMetricsCounter lookup(final CounterKey counterKey) {
        return counters.get(counterKey);
    }

    /**
     * Initializes the global {@code counters} Map with the address information of the passed {@code connection}.
     *
     * @param connection the connection to initialize the global counters map with.
     */
    @Override
    public void initForConnection(final Connection connection) {
        connection.getSources().stream()
                .map(Source::getAddresses)
                .flatMap(Collection::stream)
                .forEach(address ->
                        initCounter(connection, MetricDirection.INBOUND, address));
        connection.getTargets().stream()
                .map(Target::getAddress)
                .forEach(address ->
                        initCounter(connection, MetricDirection.OUTBOUND, address));
        initCounter(connection, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    /**
     * Resets the metric of the passed {@code connection} in the global {@code counters} Map + initializes it again.
     *
     * @param connection the connection to initialize the global counters map with.
     */
    @Override
    public void resetForConnection(final Connection connection) {
        final ConnectionId connectionId = connection.getId();
        counters.entrySet().stream()
                .filter(entry -> entry.getKey().getConnectionId().equals(connectionId))
                .forEach(entry -> entry.getValue().reset());
    }

    /**
     * Register a custom alert for the given parameters that is created by the given alert factory.
     */
    public void registerAlertFactory(final ConnectionType connectionType, final MetricType metricType,
            final MetricDirection metricDirection, final MetricsAlertFactory factory) {

        MetricsAlert.Key.from(metricDirection, metricType)
                .ifPresent(alertKey -> alertRegistry.registerCustomAlert(connectionType, alertKey, factory));
    }

    private void initCounter(final Connection connection, final MetricDirection metricDirection, final String address) {
        Arrays.stream(MetricType.values())
                .filter(metricType -> metricType.supportsDirection(metricDirection))
                .forEach(metricType -> {
                    final ConnectionId connectionId = connection.getId();
                    final ConnectionType connectionType = connection.getConnectionType();
                    final CounterKey key = CounterKey.of(connectionId, address, metricType, metricDirection);
                    final MetricsAlert delegatingAlert = new DelegatingAlert(() -> alertRegistry.getAlert(key,
                            connectionType, connectivityConfig));
                    counters.computeIfAbsent(key,
                            m -> ConnectionMetricsCounterFactory.create(metricType, metricDirection, connectionId,
                                    connectionType, address, CLOCK_UTC, delegatingAlert));
                });
    }

    /**
     * Gets the counter for the given parameter from the registry or creates it if it does not yet exist.
     *
     * @param connection connection
     * @param metricType the metricType
     * @param metricDirection the metricDirection
     * @param address the address
     * @return the counter
     */
    public ConnectionMetricsCounter getCounter(
            final Connection connection,
            final MetricType metricType,
            final MetricDirection metricDirection,
            final String address) {
        final ConnectionId connectionId = connection.getId();
        final ConnectionType connectionType = connection.getConnectionType();

        return getCounter(CLOCK_UTC, connectionId, connectionType, metricType, metricDirection, address);
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
    ConnectionMetricsCounter getCounter(
            final Clock clock,
            final ConnectionId connectionId,
            final ConnectionType connectionType,
            final MetricType metricType,
            final MetricDirection metricDirection,
            final String address) {
        final CounterKey key = CounterKey.of(connectionId, address, metricType, metricDirection);
        final MetricsAlert alert = alertRegistry.getAlert(key, connectionType, connectivityConfig);

        return counters.computeIfAbsent(key,
                m -> ConnectionMetricsCounterFactory.create(metricType, metricDirection, connectionId, connectionType,
                        address, clock, alert));
    }

    @Override
    public ConnectionMetricsCounter forOutboundDispatched(final Connection connection, final String target) {
        return getCounter(connection, MetricType.DISPATCHED, MetricDirection.OUTBOUND, target);
    }

    @Override
    public ConnectionMetricsCounter forOutboundAcknowledged(final Connection connection, final String target) {
        return getCounter(connection, MetricType.ACKNOWLEDGED, MetricDirection.OUTBOUND, target);
    }

    @Override
    public ConnectionMetricsCounter forOutboundFiltered(final Connection connection, final String target) {
        return getCounter(connection, MetricType.FILTERED, MetricDirection.OUTBOUND, target);
    }

    @Override
    public ConnectionMetricsCounter forOutboundPublished(final Connection connection, final String target) {
        return getCounter(connection, MetricType.PUBLISHED, MetricDirection.OUTBOUND, target);
    }

    @Override
    public ConnectionMetricsCounter forOutboundDropped(final Connection connection, final String target) {
        return getCounter(connection, MetricType.DROPPED, MetricDirection.OUTBOUND, target);
    }

    @Override
    public ConnectionMetricsCounter forInboundConsumed(final Connection connection, final String source) {
        return getCounter(connection, MetricType.CONSUMED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forInboundAcknowledged(final Connection connection, final String source) {
        return getCounter(connection, MetricType.ACKNOWLEDGED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forInboundMapped(final Connection connection, final String source) {
        return getCounter(connection, MetricType.MAPPED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forInboundEnforced(final Connection connection, final String source) {
        return getCounter(connection, MetricType.ENFORCED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forInboundDropped(final Connection connection, final String source) {
        return getCounter(connection, MetricType.DROPPED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forInboundThrottled(final Connection connection, final String source) {
        return getCounter(connection, MetricType.THROTTLED, MetricDirection.INBOUND, source);
    }

    @Override
    public ConnectionMetricsCounter forResponseDispatched(final Connection connection) {
        return getCounter(connection, MetricType.DISPATCHED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionMetricsCounter forResponseDropped(final Connection connection) {
        return getCounter(connection, MetricType.DROPPED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionMetricsCounter forResponseMapped(final Connection connection) {
        return getCounter(connection, MetricType.MAPPED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionMetricsCounter forResponsePublished(final Connection connection) {
        return getCounter(connection, MetricType.PUBLISHED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    @Override
    public ConnectionMetricsCounter forResponseAcknowledged(final Connection connection) {
        return getCounter(connection, MetricType.ACKNOWLEDGED, MetricDirection.OUTBOUND, RESPONSES_ADDRESS);
    }

    private static Stream<DefaultConnectionMetricsCounter> streamFor(final ConnectionId connectionId,
            final MetricDirection metricDirection) {
        return counters.entrySet()
                .stream()
                .filter(e -> e.getKey().getConnectionId().equals(connectionId))
                .filter(e -> metricDirection == e.getValue().getMetricDirection())
                .map(Map.Entry::getValue);
    }

    private static Map<String, AddressMetric> aggregateMetrics(final ConnectionId connectionId,
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
    public SourceMetrics aggregateSourceMetrics(final ConnectionId connectionId) {
        return ConnectivityModelFactory.newSourceMetrics(aggregateMetrics(connectionId, MetricDirection.INBOUND));
    }

    /**
     * Aggregate the {@link TargetMetrics} for the given connection from the counters in this registry.
     *
     * @param connectionId connection id
     * @return the {@link TargetMetrics}
     */
    public TargetMetrics aggregateTargetMetrics(final ConnectionId connectionId) {
        return ConnectivityModelFactory.newTargetMetrics(aggregateMetrics(connectionId, MetricDirection.OUTBOUND));
    }

    /**
     * Merges the passed in {@link RetrieveConnectionMetricsResponse}s into each other returning a new {@link
     * RetrieveConnectionMetricsResponse} containing the merged information.
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

        return RetrieveConnectionMetricsResponse.getBuilder(first.getEntityId(), first.getDittoHeaders())
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
    public ConnectionMetrics aggregateConnectionMetrics(
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
}
