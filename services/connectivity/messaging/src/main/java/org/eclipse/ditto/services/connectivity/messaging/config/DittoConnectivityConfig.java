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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;

/**
 * This class is the implementation of {@link ConnectivityConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DittoConnectivityConfig implements ConnectivityConfig {

    private static final String CONFIG_PATH = "connectivity";

    private final DittoServiceConfig serviceSpecificConfig;
    private final DefaultMongoDbConfig mongoDbConfig;
    private final DefaultHealthCheckConfig healthCheckConfig;
    private final DefaultConnectionConfig connectionConfig;
    private final DefaultMappingConfig mappingConfig;
    private final DefaultReconnectConfig reconnectConfig;
    private final DefaultClientConfig clientConfig;
    private final DefaultProtocolConfig protocolConfig;
    private final MonitoringConfig monitoringConfig;

    private DittoConnectivityConfig(final ScopedConfig dittoScopedConfig) {

        serviceSpecificConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoScopedConfig);
        protocolConfig = DefaultProtocolConfig.of(dittoScopedConfig);

        connectionConfig = DefaultConnectionConfig.of(serviceSpecificConfig);
        mappingConfig = DefaultMappingConfig.of(serviceSpecificConfig);
        reconnectConfig = DefaultReconnectConfig.of(serviceSpecificConfig);
        clientConfig = DefaultClientConfig.of(serviceSpecificConfig);
        monitoringConfig = DefaultMonitoringConfig.of(serviceSpecificConfig);
    }

    /**
     * Returns an instance of DittoConnectivityConfig based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConnectivityConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoConnectivityConfig(dittoScopedConfig);
    }

    @Override
    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    @Override
    public MappingConfig getMappingConfig() {
        return mappingConfig;
    }

    @Override
    public ReconnectConfig getReconnectConfig() {
        return reconnectConfig;
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
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(connectionConfig, that.connectionConfig) &&
                Objects.equals(mappingConfig, that.mappingConfig) &&
                Objects.equals(reconnectConfig, that.reconnectConfig) &&
                Objects.equals(clientConfig, that.clientConfig) &&
                Objects.equals(protocolConfig, that.protocolConfig) &&
                Objects.equals(monitoringConfig, that.monitoringConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, mongoDbConfig, healthCheckConfig, connectionConfig, mappingConfig,
                reconnectConfig, clientConfig, protocolConfig, monitoringConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", connectionConfig=" + connectionConfig +
                ", mappingConfig=" + mappingConfig +
                ", reconnectConfig=" + reconnectConfig +
                ", clientConfig=" + clientConfig +
                ", protocolConfig=" + protocolConfig +
                ", monitoringConfig=" + monitoringConfig +
                "]";
    }

}
