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

package org.eclipse.ditto.services.connectivity.messaging.monitoring;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MetricDirection;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.services.connectivity.messaging.config.MonitoringConfig;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLoggerRegistry;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.metrics.ConnectivityCounterRegistry;

/**
 * Default implementation of {@link org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitorRegistry}.
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
     * @param config the configuration to  use.
     * @return a new instance of {@code DefaultConnectionMonitorRegistry}.
     * @throws java.lang.NullPointerException if {@code config} is null.
     */
    public static DefaultConnectionMonitorRegistry fromConfig(final MonitoringConfig config) {
        checkNotNull(config);

        final ConnectionLoggerRegistry loggerRegistry = ConnectionLoggerRegistry.fromConfig(config.logger());
        final ConnectivityCounterRegistry counterRegistry = ConnectivityCounterRegistry.fromConfig(config.counter());

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
    public ConnectionMonitor forOutboundDispatched(final EntityId connectionId, final String target) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forOutboundDispatched(connectionId, target),
                connectionLoggerRegistry.forOutboundDispatched(connectionId, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundFiltered(final EntityId connectionId, final String target) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forOutboundFiltered(connectionId, target),
                connectionLoggerRegistry.forOutboundFiltered(connectionId, target))
                .build();
    }

    @Override
    public ConnectionMonitor forOutboundPublished(final EntityId connectionId, final String target) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forOutboundPublished(connectionId, target),
                connectionLoggerRegistry.forOutboundPublished(connectionId, target))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundConsumed(final EntityId connectionId, final String source) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forInboundConsumed(connectionId, source),
                connectionLoggerRegistry.forInboundConsumed(connectionId, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundMapped(final EntityId connectionId, final String source) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forInboundMapped(connectionId, source),
                connectionLoggerRegistry.forInboundMapped(connectionId, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundEnforced(final EntityId connectionId, final String source) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forInboundEnforced(connectionId, source),
                connectionLoggerRegistry.forInboundEnforced(connectionId, source))
                .build();
    }

    @Override
    public ConnectionMonitor forInboundDropped(final EntityId connectionId, final String source) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forInboundDropped(connectionId, source),
                connectionLoggerRegistry.forInboundDropped(connectionId, source))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseDispatched(final EntityId connectionId) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forResponseDispatched(connectionId),
                connectionLoggerRegistry.forResponseDispatched(connectionId))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseDropped(final EntityId connectionId) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forResponseDropped(connectionId),
                connectionLoggerRegistry.forResponseDropped(connectionId))
                .build();
    }

    @Override
    public ConnectionMonitor forResponseMapped(final EntityId connectionId) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forResponseMapped(connectionId),
                connectionLoggerRegistry.forResponseMapped(connectionId))
                .build();
    }

    @Override
    public ConnectionMonitor forResponsePublished(final EntityId connectionId) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.forResponsePublished(connectionId),
                connectionLoggerRegistry.forResponsePublished(connectionId))
                .build();
    }

    /**
     * Retrieve a specific monitor.
     * @param connectionId the connection.
     * @param metricType type of the metrics counter.
     * @param metricDirection direction of the metrics counter.
     * @param logType type of the logger.
     * @param logCategory category of the logger.
     * @param address address.
     * @return the specific monitor.
     */
    public ConnectionMonitor getMonitor(final EntityId connectionId, final MetricType metricType,
            final MetricDirection metricDirection, final LogType logType, final LogCategory logCategory,
            final String address) {
        return DefaultConnectionMonitor.builder(
                connectionCounterRegistry.getCounter(connectionId, metricType, metricDirection, address),
                connectionLoggerRegistry.getLogger(connectionId, logCategory, logType, address))
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
                ", connectionLoggerRegistry=" + connectionLoggerRegistry +
                ", connectionCounterRegistry=" + connectionCounterRegistry +
                "]";
    }

}
