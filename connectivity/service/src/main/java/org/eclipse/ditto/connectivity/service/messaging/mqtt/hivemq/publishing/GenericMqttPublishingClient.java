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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

/**
 * A generic client for sending {@link GenericMqttPublish} messages to the broker.
 */
@ThreadSafe
public abstract class GenericMqttPublishingClient implements GenericMqttClient {

    private GenericMqttPublishingClient() {
        super();
    }

    /**
     * Returns a new instance of {@code GenericMqttPublishingClient} for the specified {@code HiveMqttClientProperties}
     * argument.
     *
     * @param hiveMqttClientProperties properties which are required for creating a publising client.
     * @return the new instance.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    public static GenericMqttPublishingClient newInstance(final HiveMqttClientProperties hiveMqttClientProperties) {
        ConditionChecker.checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");

        final GenericMqttPublishingClient result;
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        final var clientIdentifierFactory = MqttPublishingClientIdentifierFactory.newInstance(
                hiveMqttClientProperties.getMqttSpecificConfig(),
                mqttConnection,
                hiveMqttClientProperties.getActorUuid()
        );
        if (ConnectionType.MQTT == mqttConnection.getConnectionType()) {
            final var mqtt3Client = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                    clientIdentifierFactory.getMqttClientIdentifier(),
                    true);
            result = ofMqtt3AsyncClient(mqtt3Client.toAsync());
        } else {
            final var mqtt5RxClient = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                    clientIdentifierFactory.getMqttClientIdentifier(),
                    true);
            result = ofMqtt5AsyncClient(mqtt5RxClient.toAsync());
        }
        return result;
    }

    /**
     * Returns an instance of {@code GenericMqttPublishingClient} that operates on the specified
     * {@code Mqtt3AsyncClient} argument.
     *
     * @param mqtt3AsyncClient the MQTT client for sending Publish messages.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3AsyncClient} is {@code null}.
     */
    static GenericMqttPublishingClient ofMqtt3AsyncClient(final Mqtt3AsyncClient mqtt3AsyncClient) {
        return new Mqtt3AsyncPublishingClient(mqtt3AsyncClient);
    }

    /**
     * Returns an instance of {@code GenericMqttPublishingClient} that operates on the specified
     * {@code Mqtt5AsyncClient} argument.
     *
     * @param mqtt5AsyncClient the MQTT client for sending Publish messages.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5AsyncClient} is {@code null}.
     */
    static GenericMqttPublishingClient ofMqtt5AsyncClient(final Mqtt5AsyncClient mqtt5AsyncClient) {
        return new Mqtt5AsyncPublishingClient(mqtt5AsyncClient);
    }

    /**
     * Sends the specified {@code GenericMqttPublish} message to the broker.
     *
     * @param genericMqttPublish the Publish message sent to the broker.
     * @return a {@code CompletionStage} which always completes normally with a {@link GenericMqttPublishResult}.
     * If an error occurred before the Publish message was sent or before an acknowledgement message was received,
     * the yielded {@code GenericMqttPublishResult} represents a failure and provides the occurred error. I.e. all
     * known possible exceptions are moved in-band to make it easier to handle and reason about the returned
     * CompletionStage.
     * @throws NullPointerException if {@code genericMqttPublish} is {@code null}.
     */
    CompletionStage<GenericMqttPublishResult> publish(final GenericMqttPublish genericMqttPublish) {
        return sendPublish(ConditionChecker.checkNotNull(genericMqttPublish, "genericMqttPublish"));
    }

    protected abstract CompletionStage<GenericMqttPublishResult> sendPublish(GenericMqttPublish genericMqttPublish);

    private static final class Mqtt3AsyncPublishingClient extends GenericMqttPublishingClient {

        private final Mqtt3AsyncClient mqtt3AsyncClient;

        private Mqtt3AsyncPublishingClient(final Mqtt3AsyncClient mqtt3AsyncClient) {
            this.mqtt3AsyncClient = ConditionChecker.checkNotNull(mqtt3AsyncClient, "mqtt3AsyncClient");
        }

        @Override
        public CompletionStage<GenericMqttConnAckStatus> connect(final GenericMqttConnect genericMqttConnect) {
            checkNotNull(genericMqttConnect, "genericMqttConnect");
            return mqtt3AsyncClient.connect(genericMqttConnect.getAsMqtt3Connect())
                    .handle((mqtt3ConnAck, throwable) -> {
                        if (null == throwable) {
                            return GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(mqtt3ConnAck.getReturnCode());
                        } else {
                            throw new MqttClientConnectException(throwable);
                        }
                    });
        }

        @Override
        public CompletionStage<Void> disconnect() {
            return mqtt3AsyncClient.disconnect();
        }

        @Override
        protected CompletionStage<GenericMqttPublishResult> sendPublish(final GenericMqttPublish genericMqttPublish) {
            return mqtt3AsyncClient.publish(genericMqttPublish.getAsMqtt3Publish())
                    .handle((mqtt3Publish, throwable) -> {
                        final GenericMqttPublishResult result;
                        if (null == throwable) {
                            result = GenericMqttPublishResult.success(genericMqttPublish);
                        } else {
                            result = GenericMqttPublishResult.failure(genericMqttPublish, throwable);
                        }
                        return result;
                    });
        }

    }

    private static final class Mqtt5AsyncPublishingClient extends GenericMqttPublishingClient {

        private final Mqtt5AsyncClient mqtt5AsyncClient;

        private Mqtt5AsyncPublishingClient(final Mqtt5AsyncClient mqtt5AsyncClient) {
            this.mqtt5AsyncClient = ConditionChecker.checkNotNull(mqtt5AsyncClient, "mqtt5AsyncClient");
        }

        @Override
        public CompletionStage<GenericMqttConnAckStatus> connect(final GenericMqttConnect genericMqttConnect) {
            checkNotNull(genericMqttConnect, "genericMqttConnect");
            return mqtt5AsyncClient.connect(genericMqttConnect.getAsMqtt5Connect())
                    .handle((mqtt5ConnAck, throwable) -> {
                        if (null == throwable) {
                            return GenericMqttConnAckStatus.ofMqtt5ConnAckReasonCode(mqtt5ConnAck.getReasonCode());
                        } else {
                            throw new MqttClientConnectException(throwable);
                        }
                    });
        }

        @Override
        public CompletionStage<Void> disconnect() {
            return mqtt5AsyncClient.disconnect();
        }

        @Override
        protected CompletionStage<GenericMqttPublishResult> sendPublish(final GenericMqttPublish genericMqttPublish) {
            return mqtt5AsyncClient.publish(genericMqttPublish.getAsMqtt5Publish())
                    .handle((mqtt5PublishResult, throwable) -> {
                        final GenericMqttPublishResult result;
                        if (null == throwable) {
                            result = mqtt5PublishResult.getError()
                                    .map(error -> GenericMqttPublishResult.failure(genericMqttPublish, error))
                                    .orElseGet(() -> GenericMqttPublishResult.success(genericMqttPublish));
                        } else {
                            result = GenericMqttPublishResult.failure(genericMqttPublish, throwable);
                        }
                        return result;
                    });
        }

    }

}
