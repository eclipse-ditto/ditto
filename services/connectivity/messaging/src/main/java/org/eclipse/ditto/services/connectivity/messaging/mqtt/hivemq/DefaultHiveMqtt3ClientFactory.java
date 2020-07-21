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
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;

/**
 * Default implementation of {@link HiveMqtt3ClientFactory}.
 */
public final class DefaultHiveMqtt3ClientFactory extends AbstractHiveMqttClientFactory
        implements HiveMqtt3ClientFactory {

    private static final DefaultHiveMqtt3ClientFactory INSTANCE = new DefaultHiveMqtt3ClientFactory();

    /**
     * @return the singleton instance of {@link DefaultHiveMqtt3ClientFactory}
     */
    public static DefaultHiveMqtt3ClientFactory getInstance() {
        return INSTANCE;
    }

    private DefaultHiveMqtt3ClientFactory() {
    }

    @Override
    public Mqtt3AsyncClient newClient(final Connection connection, final String identifier,
            final boolean allowReconnect,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener) {

        return newClientBuilder(connection, identifier, allowReconnect, connectedListener, disconnectedListener)
                .buildAsync();
    }

    @Override
    public Mqtt3ClientBuilder newClientBuilder(final Connection connection, final String identifier,
            final boolean allowReconnect,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener) {
        final Mqtt3ClientBuilder mqtt3ClientBuilder =
                configureClientBuilder(MqttClient.builder().useMqttVersion3(), connection, identifier, allowReconnect,
                        connectedListener, disconnectedListener);
        configureSimpleAuth(mqtt3ClientBuilder.simpleAuth(), connection);
        return mqtt3ClientBuilder;
    }

}
