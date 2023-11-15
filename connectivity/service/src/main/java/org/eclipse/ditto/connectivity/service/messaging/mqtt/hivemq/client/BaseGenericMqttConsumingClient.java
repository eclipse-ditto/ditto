/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import io.reactivex.Flowable;

/**
 * Base implementation of {@link GenericMqttConsumingClient}.
 */
abstract class BaseGenericMqttConsumingClient<C extends MqttClient> implements GenericMqttConsumingClient {

    private final C mqttClient;

    private BaseGenericMqttConsumingClient(final C mqttClient) {
        this.mqttClient = mqttClient;
    }

    static BaseGenericMqttConsumingClient<Mqtt3RxClient> ofMqtt3RxClient(
            final Mqtt3RxClient mqtt3RxClient
    ) {
        return new Mqtt3ConsumingClient(mqtt3RxClient);
    }

    static BaseGenericMqttConsumingClient<Mqtt5RxClient> ofMqtt5RxClient(
            final Mqtt5RxClient mqtt5RxClient
    ) {
        return new Mqtt5ConsumingClient(mqtt5RxClient);
    }

    @Override
    public String toString() {
        final var mqttClientConfig = mqttClient.getConfig();
        final var clientIdentifier = mqttClientConfig.getClientIdentifier();
        return clientIdentifier.toString();
    }

    private static final class Mqtt3ConsumingClient extends BaseGenericMqttConsumingClient<Mqtt3RxClient> {

        private final BufferingFlowableWrapper<Mqtt3Publish> bufferingFlowableWrapper;
        private boolean isDisposed = false;

        private Mqtt3ConsumingClient(final Mqtt3RxClient mqtt3RxClient) {
            super(ConditionChecker.checkNotNull(mqtt3RxClient, "mqtt3RxClient"));

            bufferingFlowableWrapper = BufferingFlowableWrapper.of(mqtt3RxClient.publishes(MqttGlobalPublishFilter.ALL, true));
        }

        @Override
        public Flowable<GenericMqttPublish> consumePublishes() {
            return bufferingFlowableWrapper.toFlowable().map(GenericMqttPublish::ofMqtt3Publish);
        }

        @Override
        public void stopBufferingPublishes() {
            bufferingFlowableWrapper.stopBuffering();
        }

        @Override
        public void dispose() {
            bufferingFlowableWrapper.dispose();
            isDisposed = true;
        }

        @Override
        public boolean isDisposed() {
            return isDisposed;
        }

    }

    private static final class Mqtt5ConsumingClient extends BaseGenericMqttConsumingClient<Mqtt5RxClient> {

        private final BufferingFlowableWrapper<Mqtt5Publish> bufferingFlowableWrapper;
        private boolean isDisposed = false;

        private Mqtt5ConsumingClient(final Mqtt5RxClient mqtt5RxClient) {
            super(checkNotNull(mqtt5RxClient, "mqtt5RxClient"));

            bufferingFlowableWrapper = BufferingFlowableWrapper.of(mqtt5RxClient.publishes(MqttGlobalPublishFilter.ALL, true));
        }

        @Override
        public Flowable<GenericMqttPublish> consumePublishes() {
            return bufferingFlowableWrapper.toFlowable().map(GenericMqttPublish::ofMqtt5Publish);
        }

        @Override
        public void stopBufferingPublishes() {
            bufferingFlowableWrapper.stopBuffering();
        }

        @Override
        public void dispose() {
            bufferingFlowableWrapper.dispose();
            isDisposed = true;
        }

        @Override
        public boolean isDisposed() {
            return isDisposed;
        }

    }

}
