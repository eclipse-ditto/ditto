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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ReconnectConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;

/**
 * Provides the configuration settings of the Connectivity service.
 * <p>
 * Java serialization is supported for {@code ConnectivityConfig}.
 * </p>
 */
@Immutable
public interface ConnectivityConfig extends ServiceSpecificConfig, WithMongoDbConfig {

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
