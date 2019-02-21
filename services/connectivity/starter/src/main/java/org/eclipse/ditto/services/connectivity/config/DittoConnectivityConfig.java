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
import org.eclipse.ditto.services.connectivity.mapping.DefaultMappingConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.DefaultReconnectConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ReconnectConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

/**
 * This class is the implementation of {@link ConnectivityConfig} for Ditto's Connectivity service.
 */
@Immutable
public final class DittoConnectivityConfig implements ConnectivityConfig, Serializable {

    private static final String CONFIG_PATH = "connectivity";
    private static final long serialVersionUID = 1833682803547451513L;

    private final DittoServiceWithMongoDbConfig serviceSpecificConfig;
    private final ConnectionConfig connectionConfig;
    private final MappingConfig mappingConfig;
    private final ReconnectConfig reconnectConfig;
    private final ClientConfig clientConfig;

    private DittoConnectivityConfig(final DittoServiceWithMongoDbConfig theServiceSpecificConfig,
            final ConnectionConfig theConnectionConfig,
            final MappingConfig theMappingConfig,
            final ReconnectConfig theReconnectConfig,
            final ClientConfig theClientConfig) {

        serviceSpecificConfig = theServiceSpecificConfig;
        connectionConfig = theConnectionConfig;
        mappingConfig = theMappingConfig;
        reconnectConfig = theReconnectConfig;
        clientConfig = theClientConfig;
    }

    /**
     * Returns an instance of {@code DittoConnectivityConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConnectivityConfig of(final ScopedConfig config) {
        final DittoServiceWithMongoDbConfig dittoServiceConfig = DittoServiceWithMongoDbConfig.of(config, CONFIG_PATH);

        return new DittoConnectivityConfig(dittoServiceConfig,
                DefaultConnectionConfig.of(dittoServiceConfig),
                DefaultMappingConfig.of(dittoServiceConfig),
                DefaultReconnectConfig.of(dittoServiceConfig),
                DefaultClientConfig.of(dittoServiceConfig));
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
        return serviceSpecificConfig.getHealthCheckConfig();
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConnectivityConfig that = (DittoConnectivityConfig) o;
        return Objects.equals(serviceSpecificConfig, that.serviceSpecificConfig) &&
                Objects.equals(connectionConfig, that.connectionConfig) &&
                Objects.equals(mappingConfig, that.mappingConfig) &&
                Objects.equals(reconnectConfig, that.reconnectConfig) &&
                Objects.equals(clientConfig, that.clientConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, connectionConfig, mappingConfig, reconnectConfig, clientConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", connectionConfig=" + connectionConfig +
                ", mappingConfig=" + mappingConfig +
                ", reconnectConfig=" + reconnectConfig +
                ", clientConfig=" + clientConfig +
                "]";
    }

}
