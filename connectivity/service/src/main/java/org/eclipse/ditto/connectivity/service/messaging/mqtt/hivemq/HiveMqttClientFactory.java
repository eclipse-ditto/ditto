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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;

/**
 * Factory used to create an MQTT client, 3 or 5.
 *
 * @param <Q> type of MQTT client.
 * @param <B> type of MQTT client builder.
 */
interface HiveMqttClientFactory<Q, B> {

    /**
     * Creates a new MQTT client.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param mqttConfig the system's mqttConfig
     * @param mqttSpecificConfig the specific MQTT config to apply based on Ditto config merged with connection
     * specific config
     * @param applyLastWillConfig whether to apply the last will configuration
     * @param connectedListener the connected listener passed to the created client
     * @param disconnectedListener the disconnected listener passed to the created client
     * @param connectionLogger the connection logger
     * @return the new client.
     */
    Q newClient(Connection connection,
            String identifier,
            MqttConfig mqttConfig,
            MqttSpecificConfig mqttSpecificConfig,
            boolean applyLastWillConfig,
            @Nullable MqttClientConnectedListener connectedListener,
            @Nullable MqttClientDisconnectedListener disconnectedListener,
            ConnectionLogger connectionLogger);

    /**
     * Creates a new MQTT client builder.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param mqttConfig the system's mqttConfig
     * @param mqttSpecificConfig the specific MQTT config to apply based on Ditto config merged with connection
     * specific config
     * @param applyLastWillConfig whether to apply the last will configuration
     * @param connectedListener the connected listener passed to the created client
     * @param disconnectedListener the disconnected listener passed to the created client
     * @param connectionLogger the connection logger
     * @return the new mqtt client builder.
     */
    B newClientBuilder(Connection connection,
            String identifier,
            MqttConfig mqttConfig,
            MqttSpecificConfig mqttSpecificConfig,
            boolean applyLastWillConfig,
            @Nullable MqttClientConnectedListener connectedListener,
            @Nullable MqttClientDisconnectedListener disconnectedListener,
            ConnectionLogger connectionLogger);

    /**
     * Creates a new client.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param mqttConfig the system's mqttConfig
     * @param mqttSpecificConfig the specific MQTT config to apply based on Ditto config merged with connection
     * specific config
     * @param applyLastWillConfig whether to apply the last will configuration
     * @param connectionLogger the connection logger
     * @return the new client.
     */
    default Q newClient(final Connection connection,
            final String identifier,
            final MqttConfig mqttConfig,
            final MqttSpecificConfig mqttSpecificConfig,
            final boolean applyLastWillConfig,
            final ConnectionLogger connectionLogger) {
        return newClient(connection, identifier, mqttConfig, mqttSpecificConfig, applyLastWillConfig,
                null, null, connectionLogger);
    }

}
