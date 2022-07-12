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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;

/**
 * Factory for creating instances of {@link GenericMqttClient}.
 */
@Immutable
public final class GenericMqttClientFactory {

    private GenericMqttClientFactory() {
        super();
    }

    /**
     * Returns a new instance of {@code GenericMqttClientFactory}.
     *
     * @return the instance.
     */
    public static GenericMqttClientFactory newInstance() {
        return new GenericMqttClientFactory();
    }

    /**
     * Returns an instance of {@link GenericMqttClient} for the specified HiveMqttClientProperties argument.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @return the new {@code GenericMqttClient}.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    public GenericMqttClient getGenericMqttClient(final HiveMqttClientProperties hiveMqttClientProperties) {
        final GenericMqttClient result;
        if (isMqtt3ProtocolVersion(hiveMqttClientProperties.getMqttConnection())) {
            result = getGenericMqttClientForMqtt3(hiveMqttClientProperties);
        } else {
            result = getGenericMqttClientForMqtt5(hiveMqttClientProperties);
        }
        return result;
    }

    private static boolean isMqtt3ProtocolVersion(final Connection mqttConnection) {
        return ConnectionType.MQTT == mqttConnection.getConnectionType();
    }

    private static GenericMqttClient getGenericMqttClientForMqtt3(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final BaseGenericMqttSubscribingClient<?> subscribingClient;
        final BaseGenericMqttPublishingClient<?> publishingClient;
        if (isSeparatePublisherClient(hiveMqttClientProperties)) {

            // Create separate HiveMQ MQTT client instance for subscribing client and publishing client.
            subscribingClient = getSubscribingClientForMqtt3(hiveMqttClientProperties);
            publishingClient = getPublishingClientForMqtt3(hiveMqttClientProperties);
        } else {

            // Re-use same HiveMQ MQTT client instance for subscribing client and publishing client.
            final var subscribingClientIdFactory =
                    MqttClientIdentifierFactory.forSubscribingClient(hiveMqttClientProperties);
            final var clientRole = ClientRole.CONSUMER_PUBLISHER;
            final var mqtt3Client = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                    subscribingClientIdFactory.getMqttClientIdentifier(),
                    clientRole);
            subscribingClient = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3Client.toRx(), clientRole);
            publishingClient =
                    BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3Client.toAsync(), clientRole);
        }
        return DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);
    }

    private static boolean isSeparatePublisherClient(final HiveMqttClientProperties hiveMqttClientProperties) {
        final var mqttSpecificConfig = hiveMqttClientProperties.getMqttSpecificConfig();
        return mqttSpecificConfig.isSeparatePublisherClient();
    }

    private static BaseGenericMqttSubscribingClient<Mqtt3RxClient> getSubscribingClientForMqtt3(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final var subscribingClientIdFactory =
                MqttClientIdentifierFactory.forSubscribingClient(hiveMqttClientProperties);
        final var clientRole = ClientRole.CONSUMER;
        return BaseGenericMqttSubscribingClient.ofMqtt3RxClient(
                HiveMqttClientFactory.getMqtt3Client(
                        hiveMqttClientProperties,
                        subscribingClientIdFactory.getMqttClientIdentifier(),
                        clientRole
                ).toRx(),
                clientRole
        );
    }

    private static BaseGenericMqttPublishingClient<Mqtt3AsyncClient> getPublishingClientForMqtt3(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final var publishingClientIdFactory = MqttClientIdentifierFactory.forPublishingClient(hiveMqttClientProperties);
        final var clientRole = ClientRole.PUBLISHER;
        return BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(
                HiveMqttClientFactory.getMqtt3Client(
                        hiveMqttClientProperties,
                        publishingClientIdFactory.getMqttClientIdentifier(),
                        clientRole
                ).toAsync(),
                clientRole
        );
    }

    private static GenericMqttClient getGenericMqttClientForMqtt5(final HiveMqttClientProperties hiveMqttClientProperties) {
        final BaseGenericMqttSubscribingClient<?> subscribingClient;
        final BaseGenericMqttPublishingClient<?> publishingClient;
        if (isSeparatePublisherClient(hiveMqttClientProperties)) {

            // Create separate HiveMQ MQTT client instance for subscribing client and publishing client.
            subscribingClient = getSubscribingClientForMqtt5(hiveMqttClientProperties);
            publishingClient = getPublishingClientForMqtt5(hiveMqttClientProperties);
        } else {

            // Re-use same HiveMQ MQTT client instance for subscribing client and publishing client.
            final var subscribingClientIdFactory =
                    MqttClientIdentifierFactory.forSubscribingClient(hiveMqttClientProperties);
            final var clientRole = ClientRole.CONSUMER_PUBLISHER;
            final var mqtt5Client = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                    subscribingClientIdFactory.getMqttClientIdentifier(),
                    clientRole);
            subscribingClient = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5Client.toRx(), clientRole);
            publishingClient =
                    BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5Client.toAsync(), clientRole);
        }
        return DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);
    }

    private static BaseGenericMqttSubscribingClient<Mqtt5RxClient> getSubscribingClientForMqtt5(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final var subscribingClientIdFactory =
                MqttClientIdentifierFactory.forSubscribingClient(hiveMqttClientProperties);
        final var clientRole = ClientRole.CONSUMER;
        return BaseGenericMqttSubscribingClient.ofMqtt5RxClient(
                HiveMqttClientFactory.getMqtt5Client(
                        hiveMqttClientProperties,
                        subscribingClientIdFactory.getMqttClientIdentifier(),
                        clientRole
                ).toRx(),
                clientRole
        );
    }

    private static BaseGenericMqttPublishingClient<Mqtt5AsyncClient> getPublishingClientForMqtt5(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        final var publishingClientIdFactory = MqttClientIdentifierFactory.forPublishingClient(hiveMqttClientProperties);
        final var clientRole = ClientRole.PUBLISHER;
        return BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(
                HiveMqttClientFactory.getMqtt5Client(
                        hiveMqttClientProperties,
                        publishingClientIdFactory.getMqttClientIdentifier(),
                        clientRole
                ).toAsync(),
                clientRole
        );
    }

}
