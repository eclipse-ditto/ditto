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
package org.eclipse.ditto.services.connectivity.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceWithMongoDbConfig;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultReconnectConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ReconnectConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;

/**
 * This class is the implementation of {@link ConnectivityConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DittoConnectivityConfig implements ConnectivityConfig, Serializable {

    private static final String CONFIG_PATH = "connectivity";

    private static final long serialVersionUID = 1833682803547451513L;

    private final DittoServiceWithMongoDbConfig serviceSpecificConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final ConnectionConfig connectionConfig;
    private final MappingConfig mappingConfig;
    private final ReconnectConfig reconnectConfig;
    private final ClientConfig clientConfig;
    private final ProtocolConfig protocolConfig;

    private DittoConnectivityConfig(final DittoServiceWithMongoDbConfig connectivityScopedConfig,
            final ProtocolConfig protocolConfig) {

        serviceSpecificConfig = connectivityScopedConfig;
        healthCheckConfig = DefaultHealthCheckConfig.of(connectivityScopedConfig);
        connectionConfig = DefaultConnectionConfig.of(connectivityScopedConfig);
        mappingConfig = DefaultMappingConfig.of(connectivityScopedConfig);
        reconnectConfig = DefaultReconnectConfig.of(connectivityScopedConfig);
        clientConfig = DefaultClientConfig.of(connectivityScopedConfig);
        this.protocolConfig = protocolConfig;
    }

    /**
     * Returns an instance of {@code DittoConnectivityConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Connectivity service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConnectivityConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoConnectivityConfig(DittoServiceWithMongoDbConfig.of(dittoScopedConfig, CONFIG_PATH),
                DefaultProtocolConfig.of(dittoScopedConfig));
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
        return serviceSpecificConfig.getMongoDbConfig();
    }

    @Override
    public ProtocolConfig getProtocolConfig() {
        return protocolConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConnectivityConfig that = (DittoConnectivityConfig) o;
        return Objects.equals(serviceSpecificConfig, that.serviceSpecificConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(connectionConfig, that.connectionConfig) &&
                Objects.equals(mappingConfig, that.mappingConfig) &&
                Objects.equals(reconnectConfig, that.reconnectConfig) &&
                Objects.equals(clientConfig, that.clientConfig) &&
                Objects.equals(protocolConfig, that.protocolConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, healthCheckConfig, connectionConfig, mappingConfig, reconnectConfig,
                clientConfig, protocolConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", connectionConfig=" + connectionConfig +
                ", mappingConfig=" + mappingConfig +
                ", reconnectConfig=" + reconnectConfig +
                ", clientConfig=" + clientConfig +
                ", protocolConfig=" + protocolConfig +
                "]";
    }

}
