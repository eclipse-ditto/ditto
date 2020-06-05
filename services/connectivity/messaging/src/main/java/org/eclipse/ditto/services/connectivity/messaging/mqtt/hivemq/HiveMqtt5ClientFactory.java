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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;

/**
 * Factory used to create {@link Mqtt5Client}s.
 *
 * @since 1.1.0
 */
public interface HiveMqtt5ClientFactory {

    /**
     * Creates a new {@link Mqtt5Client}.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param allowReconnect whether client can be configured with automatic reconnect enabled, e.g. reconnect must
     * be disabled for testing a connection
     * @param connectedListener the connected listener passed to the created client
     * @param disconnectedListener the disconnected listener passed to the created client
     * @return the new {@link Mqtt5Client}
     */
    Mqtt5Client newClient(Connection connection, String identifier, boolean allowReconnect,
            @Nullable MqttClientConnectedListener connectedListener,
            @Nullable MqttClientDisconnectedListener disconnectedListener);

    /**
     * Creates a new {@link Mqtt5ClientBuilder}.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param allowReconnect whether client can be configured with automatic reconnect enabled, e.g. reconnect must
     * be disabled for testing a connection
     * @param connectedListener the connected listener passed to the created client
     * @param disconnectedListener the disconnected listener passed to the created client
     * @return the new {@link Mqtt5ClientBuilder}
     * @since 1.2.0
     */
    Mqtt5ClientBuilder newClientBuilder(Connection connection, String identifier, boolean allowReconnect,
            @Nullable MqttClientConnectedListener connectedListener,
            @Nullable MqttClientDisconnectedListener disconnectedListener);


    /**
     * Creates a new {@link Mqtt5Client}.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param allowReconnect whether client can be configured with automatic reconnect enabled
     * @return the new {@link Mqtt5Client}
     */
    default Mqtt5Client newClient(final Connection connection, final String identifier, final boolean allowReconnect) {
        return newClient(connection, identifier, allowReconnect, null, null);
    }

}
