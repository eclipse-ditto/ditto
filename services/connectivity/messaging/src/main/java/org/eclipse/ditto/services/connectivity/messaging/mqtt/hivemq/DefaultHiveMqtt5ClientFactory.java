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

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;

/**
 * Default implementation of {@link HiveMqtt5ClientFactory}.
 */
public final class DefaultHiveMqtt5ClientFactory extends AbstractHiveMqttClientFactory
        implements HiveMqtt5ClientFactory {

    private static final DefaultHiveMqtt5ClientFactory INSTANCE = new DefaultHiveMqtt5ClientFactory();

    /**
     * @return the singleton instance of {@link DefaultHiveMqtt5ClientFactory}
     */
    public static DefaultHiveMqtt5ClientFactory getInstance() {
        return INSTANCE;
    }

    private DefaultHiveMqtt5ClientFactory() {
    }

    @Override
    public Mqtt5AsyncClient newClient(final Connection connection, final String identifier,
            final boolean allowReconnect,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener) {

        return newClientBuilder(connection, identifier, allowReconnect, connectedListener, disconnectedListener)
                .buildAsync();
    }

    @Override
    public Mqtt5ClientBuilder newClientBuilder(final Connection connection, final String identifier,
            final boolean allowReconnect,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener) {
        final Mqtt5ClientBuilder mqtt5ClientBuilder =
                configureClientBuilder(MqttClient.builder().useMqttVersion5(), connection, identifier, allowReconnect,
                        connectedListener, disconnectedListener);
        configureSimpleAuth(mqtt5ClientBuilder.simpleAuth(), connection);
        return mqtt5ClientBuilder;
    }

}
