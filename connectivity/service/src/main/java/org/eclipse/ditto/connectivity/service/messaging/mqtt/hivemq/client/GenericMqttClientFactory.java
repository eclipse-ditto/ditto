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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;

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
        throw new AssertionError();
    }

    /**
     * Returns an instance of {@link GenericMqttClient} for use in production.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @return the new {@code GenericMqttClient}.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    public static GenericMqttClient getProductiveGenericMqttClient(final HiveMqttClientProperties hiveMqttClientProperties) {
        return getGenericMqttClient(hiveMqttClientProperties, false);
    }

    private static GenericMqttClient getGenericMqttClient(final HiveMqttClientProperties hiveMqttClientProperties,
            final boolean forcefullyDisableLastWill) {

        checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");
        final GenericMqttClient result;
        final var factory = new FactoryImplementation(hiveMqttClientProperties, forcefullyDisableLastWill);
        if (isMqtt3ProtocolVersion(hiveMqttClientProperties.getMqttConnection())) {
            result = factory.getGenericMqttClientForMqtt3();
        } else {
            result = factory.getGenericMqttClientForMqtt5();
        }
        return result;
    }

    private static boolean isMqtt3ProtocolVersion(final Connection mqttConnection) {
        return ConnectionType.MQTT == mqttConnection.getConnectionType();
    }

    /**
     * Returns an instance of {@link GenericMqttClient} for testing a connection.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @return the new {@code GenericMqttClient}.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    public static GenericMqttClient getGenericMqttClientForConnectionTesting(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return getGenericMqttClient(hiveMqttClientProperties, true);
    }

    private static final class FactoryImplementation {

        private final HiveMqttClientProperties hiveMqttClientProperties;
        private final MqttSpecificConfig mqttSpecificConfig;
        private final MqttClientIdentifierFactory subscribingClientIdFactory;
        private final MqttClientIdentifierFactory publishingClientIdFactory;
        private final boolean forcefullyDisableLastWill;

        private FactoryImplementation(final HiveMqttClientProperties hiveMqttClientProperties,
                final boolean forcefullyDisableLastWill) {

            this.hiveMqttClientProperties = hiveMqttClientProperties;
            mqttSpecificConfig = hiveMqttClientProperties.getMqttSpecificConfig();
            subscribingClientIdFactory = MqttClientIdentifierFactory.forSubscribingClient(hiveMqttClientProperties);
            publishingClientIdFactory = MqttClientIdentifierFactory.forPublishingClient(hiveMqttClientProperties);
            this.forcefullyDisableLastWill = forcefullyDisableLastWill;
        }

        private GenericMqttClient getGenericMqttClientForMqtt3() {
            final BaseGenericMqttSubscribingClient<?> subscribingClient;
            final BaseGenericMqttPublishingClient<?> publishingClient;
            if (mqttSpecificConfig.separatePublisherClient()) {

                // Create separate HiveMQ MQTT client instance for subscribing client and publishing client.
                subscribingClient = getSubscribingClientForMqtt3();
                publishingClient = getPublishingClientForMqtt3();
            } else {

                // Re-use same HiveMQ MQTT client instance for subscribing client and publishing client.
                final var clientRole = ClientRole.CONSUMER_PUBLISHER;
                final var mqtt3Client = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                        subscribingClientIdFactory.getMqttClientIdentifier(),
                        !forcefullyDisableLastWill,
                        clientRole);
                subscribingClient = BaseGenericMqttSubscribingClient.ofMqtt3RxClient(mqtt3Client.toRx(), clientRole);
                publishingClient =
                        BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(mqtt3Client.toAsync(), clientRole);
            }
            return DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);
        }

        private BaseGenericMqttSubscribingClient<Mqtt3RxClient> getSubscribingClientForMqtt3() {
            final var clientRole = ClientRole.CONSUMER;
            return BaseGenericMqttSubscribingClient.ofMqtt3RxClient(
                    HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                            subscribingClientIdFactory.getMqttClientIdentifier(),
                            false,
                            clientRole
                    ).toRx(),
                    clientRole
            );
        }

        private BaseGenericMqttPublishingClient<Mqtt3AsyncClient> getPublishingClientForMqtt3() {
            final var clientRole = ClientRole.PUBLISHER;
            return BaseGenericMqttPublishingClient.ofMqtt3AsyncClient(
                    HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                            publishingClientIdFactory.getMqttClientIdentifier(),
                            !forcefullyDisableLastWill,
                            clientRole
                    ).toAsync(),
                    clientRole
            );
        }

        private GenericMqttClient getGenericMqttClientForMqtt5() {
            final BaseGenericMqttSubscribingClient<?> subscribingClient;
            final BaseGenericMqttPublishingClient<?> publishingClient;
            if (mqttSpecificConfig.separatePublisherClient()) {

                // Create separate HiveMQ MQTT client instance for subscribing client and publishing client.
                subscribingClient = getSubscribingClientForMqtt5();
                publishingClient = getPublishingClientForMqtt5();
            } else {

                // Re-use same HiveMQ MQTT client instance for subscribing client and publishing client.
                final var clientRole = ClientRole.CONSUMER_PUBLISHER;
                final var mqtt5Client = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                        subscribingClientIdFactory.getMqttClientIdentifier(),
                        !forcefullyDisableLastWill,
                        clientRole);
                subscribingClient = BaseGenericMqttSubscribingClient.ofMqtt5RxClient(mqtt5Client.toRx(), clientRole);
                publishingClient =
                        BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(mqtt5Client.toAsync(), clientRole);
            }
            return DefaultGenericMqttClient.newInstance(subscribingClient, publishingClient, hiveMqttClientProperties);
        }

        private BaseGenericMqttSubscribingClient<Mqtt5RxClient> getSubscribingClientForMqtt5() {
            final var clientRole = ClientRole.CONSUMER;
            return BaseGenericMqttSubscribingClient.ofMqtt5RxClient(
                    HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                            subscribingClientIdFactory.getMqttClientIdentifier(),
                            false,
                            clientRole
                    ).toRx(),
                    clientRole
            );
        }

        private BaseGenericMqttPublishingClient<Mqtt5AsyncClient> getPublishingClientForMqtt5(
        ) {
            final var clientRole = ClientRole.PUBLISHER;
            return BaseGenericMqttPublishingClient.ofMqtt5AsyncClient(
                    HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                            publishingClientIdFactory.getMqttClientIdentifier(),
                            !forcefullyDisableLastWill,
                            clientRole
                    ).toAsync(),
                    clientRole
            );
        }

    }

}
