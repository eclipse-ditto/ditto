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
package org.eclipse.ditto.services.connectivity.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ReconnectConfig;
import org.eclipse.ditto.services.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.services.utils.protocol.config.WithProtocolConfig;

/**
 * Provides the configuration settings of the Connectivity service.
 * <p>
 * Java serialization is supported for {@code ConnectivityConfig}.
 * </p>
 */
@Immutable
public interface ConnectivityConfig
        extends ServiceSpecificConfig, WithHealthCheckConfig, WithMongoDbConfig, WithProtocolConfig {

    /**
     * Returns the config of connections.
     *
     * @return the config.
     */
    ConnectionConfig getConnectionConfig();

    /**
     * Returns the config for Connectivity service's message mapping.
     *
     * @return the config.
     */
    MappingConfig getMappingConfig();

    /**
     * Returns the config for Connectivity service's reconnect behaviour.
     *
     * @return the config.
     */
    ReconnectConfig getReconnectConfig();

    /**
     * Returns the config for the Connectivity service's client.
     *
     * @return the config.
     */
    ClientConfig getClientConfig();

}
