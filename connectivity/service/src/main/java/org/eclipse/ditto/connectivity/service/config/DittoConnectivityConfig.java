/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.DefaultMappingConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.DefaultPersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.DefaultPingConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.eclipse.ditto.internal.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.internal.utils.protocol.config.ProtocolConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

/**
 * This class is the implementation of {@link ConnectivityConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DittoConnectivityConfig implements ConnectivityConfig {

    private static final String CONFIG_PATH = "connectivity";

    private final DittoServiceConfig serviceSpecificConfig;
    private final PersistenceOperationsConfig persistenceOperationsConfig;
    private final MongoDbConfig mongoDbConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final ConnectionConfig connectionConfig;
    private final PingConfig pingConfig;
    private final ConnectionIdsRetrievalConfig connectionIdsRetrievalConfig;
    private final ClientConfig clientConfig;
    private final ProtocolConfig protocolConfig;
    private final MonitoringConfig monitoringConfig;
    private final MappingConfig mappingConfig;
    private final AcknowledgementConfig acknowledgementConfig;
    private final TunnelConfig tunnelConfig;

    private DittoConnectivityConfig(final ScopedConfig dittoScopedConfig) {
        serviceSpecificConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        persistenceOperationsConfig = DefaultPersistenceOperationsConfig.of(dittoScopedConfig);
        mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoScopedConfig);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);
        connectionConfig = DefaultConnectionConfig.of(serviceSpecificConfig);
        pingConfig = DefaultPingConfig.of(serviceSpecificConfig);
        connectionIdsRetrievalConfig = DefaultConnectionIdsRetrievalConfig.of(serviceSpecificConfig);
        clientConfig = DefaultClientConfig.of(serviceSpecificConfig);
        monitoringConfig = DefaultMonitoringConfig.of(serviceSpecificConfig);
        mappingConfig = DefaultMappingConfig.of(serviceSpecificConfig);
        acknowledgementConfig = DefaultAcknowledgementConfig.of(serviceSpecificConfig);
        tunnelConfig = DefaultTunnelConfig.of(serviceSpecificConfig);
    }

    /**
     * Returns an instance of DittoConnectivityConfig based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConnectivityConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoConnectivityConfig(
                ConfigWithFallback.newInstance(dittoScopedConfig, ConnectivityConfigValue.values()));
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @Override
    public PingConfig getPingConfig() {
        return pingConfig;
    }

    @Override
    public ConnectionIdsRetrievalConfig getConnectionIdsRetrievalConfig() {
        return connectionIdsRetrievalConfig;
    }

    @Override
    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return serviceSpecificConfig.getClusterConfig();
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return serviceSpecificConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return serviceSpecificConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return serviceSpecificConfig.getMetricsConfig();
    }

    @Override
    public TracingConfig getTracingConfig() {
        return serviceSpecificConfig.getTracingConfig();
    }

    @Override
    public PersistenceOperationsConfig getPersistenceOperationsConfig() {
        return persistenceOperationsConfig;
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @Override
    public ProtocolConfig getProtocolConfig() {
        return protocolConfig;
    }

    @Override
    public MonitoringConfig getMonitoringConfig() {
        return monitoringConfig;
    }

    @Override
    public MappingConfig getMappingConfig() {
        return mappingConfig;
    }

    @Override
    public AcknowledgementConfig getAcknowledgementConfig() {
        return acknowledgementConfig;
    }

    @Override
    public TunnelConfig getTunnelConfig() {
        return tunnelConfig;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConnectivityConfig that = (DittoConnectivityConfig) o;
        return Objects.equals(serviceSpecificConfig, that.serviceSpecificConfig) &&
                Objects.equals(persistenceOperationsConfig, that.persistenceOperationsConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(connectionConfig, that.connectionConfig) &&
                Objects.equals(pingConfig, that.pingConfig) &&
                Objects.equals(connectionIdsRetrievalConfig, that.connectionIdsRetrievalConfig) &&
                Objects.equals(clientConfig, that.clientConfig) &&
                Objects.equals(protocolConfig, that.protocolConfig) &&
                Objects.equals(monitoringConfig, that.monitoringConfig) &&
                Objects.equals(mappingConfig, that.mappingConfig) &&
                Objects.equals(acknowledgementConfig, that.acknowledgementConfig) &&
                Objects.equals(tunnelConfig, that.tunnelConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, persistenceOperationsConfig, mongoDbConfig, healthCheckConfig,
                connectionConfig, pingConfig, connectionIdsRetrievalConfig, clientConfig, protocolConfig,
                monitoringConfig, mappingConfig, acknowledgementConfig, tunnelConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", persistenceOperationsConfig=" + persistenceOperationsConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", connectionConfig=" + connectionConfig +
                ", pingConfig=" + pingConfig +
                ", connectionIdsRetrievalConfig=" + connectionIdsRetrievalConfig +
                ", clientConfig=" + clientConfig +
                ", protocolConfig=" + protocolConfig +
                ", monitoringConfig=" + monitoringConfig +
                ", mappingConfig=" + mappingConfig +
                ", acknowledgementConfig" + acknowledgementConfig +
                ", tunnelConfig" + tunnelConfig +
                "]";
    }

}
