/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.monitoring;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.model.MetricDirection;
import org.eclipse.ditto.connectivity.model.MetricType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.metrics.ConnectivityCounterRegistry;

/**
 * Default implementation of {@link ConnectionMonitorRegistry}.
 */
public final class DefaultConnectionMonitorRegistry implements ConnectionMonitorRegistry<ConnectionMonitor> {

    private final ConnectionLoggerRegistry connectionLoggerRegistry;
    private final ConnectivityCounterRegistry connectionCounterRegistry;

    private DefaultConnectionMonitorRegistry(final ConnectionLoggerRegistry connectionLoggerRegistry,
            final ConnectivityCounterRegistry connectionCounterRegistry) {
        this.connectionLoggerRegistry = connectionLoggerRegistry;
        this.connectionCounterRegistry = connectionCounterRegistry;
    }

    /**
     * Builds a new {@code DefaultConnectionMonitorRegistry} from a configuration.
     *
     * @param connectivityConfig the configuration to  use.
     * @return a new instance of {@code DefaultConnectionMonitorRegistry}.
     * @throws java.lang.NullPointerException if {@code connectivityConfig} is null.
     */
    public static DefaultConnectionMonitorRegistry fromConfig(final ConnectivityConfig connectivityConfig) {
        checkNotNull(connectivityConfig);

        final ConnectionLoggerRegistry loggerRegistry =
                ConnectionLoggerRegistry.fromConfig(connectivityConfig.getMonitoringConfig().logger());
        final ConnectivityCounterRegistry counterRegistry = ConnectivityCounterRegistry.newInstance(connectivityConfig);

        return new DefaultConnectionMonitorRegistry(loggerRegistry, counterRegistry);
    }

    @Override
    public void initForConnection(final Connection connection) {
        this.connectionCounterRegistry.initForConnection(connection);
        this.connectionLoggerRegistry.initForConnection(connection);
    }

    @Override
    public void resetForConnection(final Connection connection) {
        this.connectionCounterRegistry.resetForConnection(connection);
        this.connectionLoggerRegistry.resetForConnection(connection);
    }

    @Override
    public ConnectionMonitor forOutboundDispatched(final Connection connection, final String target) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forOutboundDispatched(connection, target),
                        connectionLoggerRegistry.forOutboundDispatched(connection, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundAcknowledged(final Connection connection, final String target) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forOutboundAcknowledged(connection, target),
                        connectionLoggerRegistry.forOutboundAcknowledged(connection, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundFiltered(final Connection connection, final String target) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forOutboundFiltered(connection, target),
                        connectionLoggerRegistry.forOutboundFiltered(connection, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundPublished(final Connection connection, final String target) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forOutboundPublished(connection, target),
                        connectionLoggerRegistry.forOutboundPublished(connection, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundDropped(final Connection connection, final String target) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forOutboundDropped(connection, target),
                        connectionLoggerRegistry.forOutboundDropped(connection, target))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundConsumed(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundConsumed(connection, source),
                        connectionLoggerRegistry.forInboundConsumed(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundAcknowledged(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundAcknowledged(connection, source),
                        connectionLoggerRegistry.forInboundAcknowledged(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundThrottled(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundThrottled(connection, source),
                        connectionLoggerRegistry.forInboundThrottled(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundMapped(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundMapped(connection, source),
                        connectionLoggerRegistry.forInboundMapped(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundEnforced(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundEnforced(connection, source),
                        connectionLoggerRegistry.forInboundEnforced(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundDropped(final Connection connection, final String source) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forInboundDropped(connection, source),
                        connectionLoggerRegistry.forInboundDropped(connection, source))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseDispatched(final Connection connection) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forResponseDispatched(connection),
                        connectionLoggerRegistry.forResponseDispatched(connection))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseDropped(final Connection connection) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forResponseDropped(connection),
                        connectionLoggerRegistry.forResponseDropped(connection))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseMapped(final Connection connection) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forResponseMapped(connection),
                        connectionLoggerRegistry.forResponseMapped(connection))
                .build();
    }

    @Override
    public ConnectionMonitor forResponsePublished(final Connection connection) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forResponsePublished(connection),
                        connectionLoggerRegistry.forResponsePublished(connection))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseAcknowledged(final Connection connection) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.forResponseAcknowledged(connection),
                        connectionLoggerRegistry.forResponseAcknowledged(connection))
                .build();
    }

    /**
     * Retrieve a specific monitor.
     *
     * @param connection the connection.
     * @param metricType type of the metrics counter.
     * @param metricDirection direction of the metrics counter.
     * @param logType type of the logger.
     * @param logCategory category of the logger.
     * @param address address.
     * @return the specific monitor.
     */
    public ConnectionMonitor getMonitor(final Connection connection, final MetricType metricType,
            final MetricDirection metricDirection, final LogType logType, final LogCategory logCategory,
            final String address) {
        return DefaultConnectionMonitor.builder(
                        connectionCounterRegistry.getCounter(connection, metricType, metricDirection, address),
                        connectionLoggerRegistry.getLogger(connection.getId(), logCategory, logType, address))
                .build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionMonitorRegistry that = (DefaultConnectionMonitorRegistry) o;
        return Objects.equals(connectionLoggerRegistry, that.connectionLoggerRegistry) &&
                Objects.equals(connectionCounterRegistry, that.connectionCounterRegistry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionLoggerRegistry, connectionCounterRegistry);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "connectionLoggerRegistry=" + connectionLoggerRegistry +
                ", connectionCounterRegistry=" + connectionCounterRegistry +
                "]";
    }

}
