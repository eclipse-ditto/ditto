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

import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

/**
 * Default implementation of {@link GenericMqttPublishingClient}.
 */
@ThreadSafe
abstract class BaseGenericMqttPublishingClient<C extends MqttClient>
        implements GenericMqttConnectableClient, GenericMqttPublishingClient {

    private final C mqttClient;
    private final GenericMqttConnectableClient connectableClient;
    private final ClientRole clientRole;

    private BaseGenericMqttPublishingClient(final C mqttClient,
            final GenericMqttConnectableClient connectableClient,
            final ClientRole clientRole) {

        this.mqttClient = mqttClient;
        this.connectableClient = connectableClient;
        this.clientRole = ConditionChecker.checkNotNull(clientRole, "clientRole");
    }

    /**
     * Returns an instance of {@code GenericMqttPublishingClient} that operates on the specified
     * {@code Mqtt3AsyncClient} argument.
     *
     * @param mqtt3AsyncClient the MQTT client for sending Publish messages.
     * @param clientRole the role of the returned client.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static BaseGenericMqttPublishingClient<Mqtt3AsyncClient> ofMqtt3AsyncClient(final Mqtt3AsyncClient mqtt3AsyncClient,
            final ClientRole clientRole) {

        ConditionChecker.checkNotNull(mqtt3AsyncClient, "mqtt3AsyncClient");
        return new Mqtt3AsyncPublishingClient(mqtt3AsyncClient,
                BaseGenericMqttConnectableClient.ofMqtt3AsyncClient(mqtt3AsyncClient),
                clientRole);
    }

    /**
     * Returns an instance of {@code GenericMqttPublishingClient} that operates on the specified
     * {@code Mqtt5AsyncClient} argument.
     *
     * @param mqtt5AsyncClient the MQTT client for sending Publish messages.
     * @param clientRole the role of the returned client.
     * @return the instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static BaseGenericMqttPublishingClient<Mqtt5AsyncClient> ofMqtt5AsyncClient(final Mqtt5AsyncClient mqtt5AsyncClient,
            final ClientRole clientRole) {

        ConditionChecker.checkNotNull(mqtt5AsyncClient, "mqtt5AsyncClient");
        return new Mqtt5AsyncPublishingClient(mqtt5AsyncClient,
                BaseGenericMqttConnectableClient.ofMqtt5AsyncClient(mqtt5AsyncClient),
                clientRole);
    }

    @Override
    public CompletionStage<GenericMqttPublishResult> publish(final GenericMqttPublish genericMqttPublish) {
        return sendPublish(ConditionChecker.checkNotNull(genericMqttPublish, "genericMqttPublish"), mqttClient);
    }

    protected abstract CompletionStage<GenericMqttPublishResult> sendPublish(GenericMqttPublish genericMqttPublish,
            C mqttClient);

    @Override
    public CompletionStage<Void> connect(final GenericMqttConnect genericMqttConnect) {
        return connectableClient.connect(genericMqttConnect);
    }

    @Override
    public CompletionStage<Void> disconnect() {
        return connectableClient.disconnect();
    }

    @Override
    public String toString() {
        final var mqttClientConfig = mqttClient.getConfig();
        return clientRole +
                mqttClientConfig.getClientIdentifier().map(clientIdentifier -> ":" + clientIdentifier).orElse("");
    }

    private static final class Mqtt3AsyncPublishingClient extends BaseGenericMqttPublishingClient<Mqtt3AsyncClient> {

        private Mqtt3AsyncPublishingClient(final Mqtt3AsyncClient mqtt3AsyncClient,
                final GenericMqttConnectableClient genericMqttConnectingClient,
                final ClientRole clientRole) {

            super(mqtt3AsyncClient, genericMqttConnectingClient, clientRole);
        }

        @Override
        protected CompletionStage<GenericMqttPublishResult> sendPublish(final GenericMqttPublish genericMqttPublish,
                final Mqtt3AsyncClient mqtt3AsyncClient) {

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

    private static final class Mqtt5AsyncPublishingClient extends BaseGenericMqttPublishingClient<Mqtt5AsyncClient> {

        private Mqtt5AsyncPublishingClient(final Mqtt5AsyncClient mqtt5AsyncClient,
                final GenericMqttConnectableClient genericMqttConnectingClient,
                final ClientRole clientRole) {

            super(mqtt5AsyncClient, genericMqttConnectingClient, clientRole);
        }

        @Override
        protected CompletionStage<GenericMqttPublishResult> sendPublish(final GenericMqttPublish genericMqttPublish,
                final Mqtt5AsyncClient mqtt5AsyncClient) {

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
