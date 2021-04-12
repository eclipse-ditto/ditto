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

import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.services.connectivity.messaging.tunnel.SshTunnelState;

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


    /**
     * @return the an instance of {@link DefaultHiveMqtt3ClientFactory}
     */
    public static DefaultHiveMqtt3ClientFactory getInstance(final Supplier<SshTunnelState> tunnelConfigSupplier) {
        return new DefaultHiveMqtt3ClientFactory(tunnelConfigSupplier);
    }

    private DefaultHiveMqtt3ClientFactory(
            final Supplier<SshTunnelState> tunnelConfigSupplier) {
        super(tunnelConfigSupplier);
    }

    @Override
    public Mqtt3AsyncClient newClient(final Connection connection, final String identifier,
            final boolean allowReconnect,
            final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger) {

        return newClientBuilder(connection, identifier, allowReconnect, applyLastWillConfig, connectedListener,
                disconnectedListener, connectionLogger).buildAsync();
    }

    @Override
    public Mqtt3ClientBuilder newClientBuilder(final Connection connection, final String identifier,
            final boolean allowReconnect, final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger) {
        final Mqtt3ClientBuilder mqtt3ClientBuilder =
                configureClientBuilder(MqttClient.builder().useMqttVersion3(), connection, identifier, allowReconnect,
                        connectedListener, disconnectedListener, connectionLogger);
        configureSimpleAuth(mqtt3ClientBuilder.simpleAuth(), connection);
        if (applyLastWillConfig) {
            configureWillPublish(mqtt3ClientBuilder, connection);
        }
        return mqtt3ClientBuilder;
    }

}
