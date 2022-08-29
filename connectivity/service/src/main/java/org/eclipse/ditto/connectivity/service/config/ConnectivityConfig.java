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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.connectivity.service.config.mapping.MappingConfig;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.WithPersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistentactors.config.PingConfig;
import org.eclipse.ditto.internal.utils.protocol.config.WithProtocolConfig;

import com.typesafe.config.Config;

/**
 * Provides the configuration settings of the Connectivity service.
 */
@Immutable
public interface ConnectivityConfig extends ServiceSpecificConfig, WithHealthCheckConfig,
        WithPersistenceOperationsConfig, WithMongoDbConfig, WithProtocolConfig {

    /**
     * Returns the config of connections.
     *
     * @return the config.
     */
    ConnectionConfig getConnectionConfig();

    /**
     * Returns the config for Connectivity service's reconnect (wakeup for connection persistence actors) behaviour.
     *
     * @return the config.
     */
    PingConfig getPingConfig();

    /**
     * Returns the config for Connectivity service's behaviour for retrieval of connection ids.
     *
     * @return the config.
     */
    ConnectionIdsRetrievalConfig getConnectionIdsRetrievalConfig();

    /**
     * Returns the config for the Connectivity service's client.
     *
     * @return the config.
     */
    ClientConfig getClientConfig();

    /**
     * Returns the config for the Connectivity service's monitoring features (connection logs and metrics).
     *
     * @return the config.
     */
    MonitoringConfig getMonitoringConfig();

    /**
     * Returns the config for Connectivity service's message mapping.
     *
     * @return the config.
     */
    MappingConfig getMappingConfig();

    /**
     * Returns the configuration for acknowledgement handling.
     *
     * @return the config.
     */
    AcknowledgementConfig getAcknowledgementConfig();

    /**
     * Returns the configuration for ssh tunneling.
     *
     * @return the config.
     */
    TunnelConfig getTunnelConfig();

    /**
     * Read the static connectivity config from an actor system.
     *
     * @param config the config to parse.
     * @return the connectivity config.
     */
    static ConnectivityConfig of(final Config config) {
        return DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config));
    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ConnectivityConfig}.
     */
    enum ConnectivityConfigValue implements KnownConfigValue {
        ;
        private final String path;
        private final Object defaultValue;

        ConnectivityConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
