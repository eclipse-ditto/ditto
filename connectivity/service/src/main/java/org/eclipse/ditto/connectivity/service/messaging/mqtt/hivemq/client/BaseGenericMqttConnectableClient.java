/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

/**
 * Base implementation of {@link GenericMqttConnectableClient}.
 */
abstract class BaseGenericMqttConnectableClient<C extends MqttClient> implements GenericMqttConnectableClient {

    private final C mqttClient;

    private BaseGenericMqttConnectableClient(final C mqttClient) {
        this.mqttClient = mqttClient;
    }

    static BaseGenericMqttConnectableClient<Mqtt3AsyncClient> ofMqtt3AsyncClient(
            final Mqtt3AsyncClient mqtt3AsyncClient
    ) {
        return new Mqtt3ConnectingClient(mqtt3AsyncClient);
    }

    static BaseGenericMqttConnectableClient<Mqtt5AsyncClient> ofMqtt5AsyncClient(
            final Mqtt5AsyncClient mqtt5AsyncClient
    ) {
        return new Mqtt5ConnectingClient(mqtt5AsyncClient);
    }

    @Override
    public CompletionStage<Void> connect(final GenericMqttConnect genericMqttConnect) {
        final CompletionStage<Void> result;

        // Avoids connecting the same HiveMQ client twice.
        if (isConnectedOrReconnect()) {
            result = CompletableFuture.completedFuture(null);
        } else {
            result = sendConnect(checkNotNull(genericMqttConnect, "genericMqttConnect"), mqttClient);
        }
        return result;
    }

    private boolean isConnectedOrReconnect() {
        final var mqttClientState = mqttClient.getState();
        return mqttClientState.isConnectedOrReconnect();
    }

    protected abstract CompletionStage<Void> sendConnect(GenericMqttConnect genericMqttConnect, C mqttClient);

    @Override
    public CompletionStage<Void> disconnect() {
        final CompletionStage<Void> result;
        if (isConnectedOrReconnect()) {
            result = sendDisconnect(mqttClient);
        } else {
            return CompletableFuture.completedFuture(null);
        }
        return result;
    }

    protected abstract CompletionStage<Void> sendDisconnect(C mqttClient);

    @Override
    public String toString() {
        final var mqttClientConfig = mqttClient.getConfig();
        final var clientIdentifier = mqttClientConfig.getClientIdentifier();
        return clientIdentifier.toString();
    }

    private static final class Mqtt3ConnectingClient extends BaseGenericMqttConnectableClient<Mqtt3AsyncClient> {

        private Mqtt3ConnectingClient(final Mqtt3AsyncClient mqtt3AsyncClient) {
            super(ConditionChecker.checkNotNull(mqtt3AsyncClient, "mqtt3AsyncClient"));
        }

        @Override
        protected CompletionStage<Void> sendConnect(final GenericMqttConnect genericMqttConnect,
                final Mqtt3AsyncClient mqtt3AsyncClient) {

            return mqtt3AsyncClient.connect(genericMqttConnect.getAsMqtt3Connect())
                    .handle((mqtt3ConnAck, throwable) -> {
                        if (null == throwable) {
                            return null;
                        } else {
                            throw new MqttClientConnectException(throwable);
                        }
                    });
        }

        @Override
        protected CompletionStage<Void> sendDisconnect(final Mqtt3AsyncClient mqtt3AsyncClient) {
            return mqtt3AsyncClient.disconnect();
        }

    }

    private static final class Mqtt5ConnectingClient extends BaseGenericMqttConnectableClient<Mqtt5AsyncClient> {

        private Mqtt5ConnectingClient(final Mqtt5AsyncClient mqtt5AsyncClient) {
            super(checkNotNull(mqtt5AsyncClient, "mqtt5AsyncClient"));
        }

        @Override
        protected CompletionStage<Void> sendConnect(final GenericMqttConnect genericMqttConnect,
                final Mqtt5AsyncClient mqtt5AsyncClient) {

            return mqtt5AsyncClient.connect(genericMqttConnect.getAsMqtt5Connect())
                    .handle((mqtt5ConnAck, throwable) -> {
                        if (null == throwable) {
                            return null;
                        } else {
                            throw new MqttClientConnectException(throwable);
                        }
                    });
        }

        @Override
        protected CompletionStage<Void> sendDisconnect(final Mqtt5AsyncClient mqtt5AsyncClient) {
            return mqtt5AsyncClient.disconnect();
        }

    }

}
