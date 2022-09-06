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

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.rx.FlowableWithSingle;

/**
 * Generic client for subscribing to topics at the MQTT broker.
 */
abstract class BaseGenericMqttSubscribingClient<C extends MqttClient>
        implements GenericMqttConnectableClient, GenericMqttSubscribingClient {

    private final C mqttClient;
    private final GenericMqttConnectableClient connectingClient;
    private final ClientRole clientRole;

    private BaseGenericMqttSubscribingClient(final C mqttClient,
            final GenericMqttConnectableClient connectingClient,
            final ClientRole clientRole) {

        this.mqttClient = mqttClient;
        this.connectingClient = connectingClient;
        this.clientRole = checkNotNull(clientRole, "clientRole");
    }

    /**
     * Returns an instance of {@code BaseGenericMqttSubscribingClient} that operates on the specified
     * {@code Mqtt3RxClient} argument.
     *
     * @param mqtt3RxClient the MQTT client for subscribing to topics.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3AsyncClient} is {@code null}.
     */
    static BaseGenericMqttSubscribingClient<Mqtt3RxClient> ofMqtt3RxClient(final Mqtt3RxClient mqtt3RxClient,
            final ClientRole clientRole) {

        checkNotNull(mqtt3RxClient, "mqtt3RxClient");
        return new Mqtt3RxSubscribingClient(mqtt3RxClient,
                BaseGenericMqttConnectableClient.ofMqtt3AsyncClient(mqtt3RxClient.toAsync()),
                clientRole);
    }

    /**
     * Returns an instance of {@code BaseGenericMqttSubscribingClient} that operates on the specified
     * {@code Mqtt5RxClient} argument.
     *
     * @param mqtt5RxClient the MQTT client for subscribing to topics.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5RxClient} is {@code null}.
     */
    static BaseGenericMqttSubscribingClient<Mqtt5RxClient> ofMqtt5RxClient(final Mqtt5RxClient mqtt5RxClient,
            final ClientRole clientRole) {

        checkNotNull(mqtt5RxClient, "mqtt5RxClient");
        return new Mqtt5RxSubscribingClient(mqtt5RxClient,
                BaseGenericMqttConnectableClient.ofMqtt5AsyncClient(mqtt5RxClient.toAsync()),
                clientRole);
    }

    private static List<MqttTopicFilter> getSubscriptionTopicFilters(final GenericMqttSubscribe genericMqttSubscribe) {
        return genericMqttSubscribe.genericMqttSubscriptions()
                .map(GenericMqttSubscription::getMqttTopicFilter)
                .toList();
    }

    @Override
    public FlowableWithSingle<GenericMqttPublish, GenericMqttSubAck> subscribePublishesWithManualAcknowledgement(
            final GenericMqttSubscribe genericMqttSubscribe
    ) {
        return consumeIncomingPublishes(mqttClient, genericMqttSubscribe, true)
                .mapSingle(genericMqttSubAck -> {
                    final var failedSubscriptions = getFailedSubscriptionStatuses(genericMqttSubAck,
                            getSubscriptionTopicFilters(genericMqttSubscribe));
                    if (failedSubscriptions.isEmpty()) {
                        return genericMqttSubAck;
                    } else {

                        /*
                         * The assumption that only some subscriptions failed is
                         * correct here.
                         * If all subscriptions failed then, according to
                         * HiveMQ API doc, the stream would have failed which would
                         * be handled in branch "onErrorResumeNext" in the specific
                         * protocol version client implementation.
                         */
                        throw new SomeSubscriptionsFailedException(failedSubscriptions);
                    }
                });
    }

    protected abstract FlowableWithSingle<GenericMqttPublish, GenericMqttSubAck> consumeIncomingPublishes(C mqttClient,
            GenericMqttSubscribe mqttSubscribe,
            boolean manualAcknowledgement);

    private static List<SubscriptionStatus> getFailedSubscriptionStatuses(
            final GenericMqttSubAck genericMqttSubAck,
            final List<MqttTopicFilter> subscriptionTopicFilters
    ) {
        return Zipper.zipIterables(subscriptionTopicFilters, genericMqttSubAck.getGenericMqttSubAckStatuses())
                .filter(zip -> zip.b().isError())
                .map(zip -> SubscriptionStatus.newInstance(zip.a(), zip.b()))
                .toList();
    }

    @Override
    public CompletionStage<Void> connect(final GenericMqttConnect genericMqttConnect) {
        return connectingClient.connect(genericMqttConnect);
    }

    @Override
    public CompletionStage<Void> disconnect() {
        return connectingClient.disconnect();
    }

    @Override
    public String toString() {
        final var mqttClientConfig = mqttClient.getConfig();
        return clientRole +
                mqttClientConfig.getClientIdentifier().map(clientIdentifier -> ":" + clientIdentifier).orElse("");
    }

    private static final class Mqtt3RxSubscribingClient extends BaseGenericMqttSubscribingClient<Mqtt3RxClient> {

        private Mqtt3RxSubscribingClient(final Mqtt3RxClient mqtt3RxClient,
                final GenericMqttConnectableClient genericMqttConnectingClient,
                final ClientRole clientRole) {

            super(mqtt3RxClient, genericMqttConnectingClient, clientRole);
        }

        @Override
        protected FlowableWithSingle<GenericMqttPublish, GenericMqttSubAck> consumeIncomingPublishes(
                final Mqtt3RxClient mqtt3RxClient,
                final GenericMqttSubscribe mqttSubscribe,
                final boolean manualAcknowledgement) {

            return mqtt3RxClient.subscribePublishes(mqttSubscribe.getAsMqtt3Subscribe(), manualAcknowledgement)
                    .mapError(error -> {
                        final MqttSubscribeException result;
                        if (error instanceof Mqtt3SubAckException mqtt3SubAckException) {
                            result = new AllSubscriptionsFailedException(
                                    getFailedSubscriptionStatuses(
                                            GenericMqttSubAck.ofMqtt3SubAck(mqtt3SubAckException.getMqttMessage()),
                                            getSubscriptionTopicFilters(mqttSubscribe)
                                    ),
                                    mqtt3SubAckException
                            );
                        } else {
                            result = new MqttSubscribeException(error);
                        }
                        return result;
                    })
                    .mapBoth(GenericMqttPublish::ofMqtt3Publish, GenericMqttSubAck::ofMqtt3SubAck);
        }

    }

    private static final class Mqtt5RxSubscribingClient extends BaseGenericMqttSubscribingClient<Mqtt5RxClient> {

        private Mqtt5RxSubscribingClient(final Mqtt5RxClient mqtt5RxClient,
                final GenericMqttConnectableClient genericMqttConnectingClient,
                final ClientRole clientRole) {

            super(mqtt5RxClient, genericMqttConnectingClient, clientRole);
        }

        @Override
        protected FlowableWithSingle<GenericMqttPublish, GenericMqttSubAck> consumeIncomingPublishes(
                final Mqtt5RxClient mqtt5RxClient,
                final GenericMqttSubscribe mqttSubscribe,
                final boolean manualAcknowledgement) {

            return mqtt5RxClient.subscribePublishes(mqttSubscribe.getAsMqtt5Subscribe(), manualAcknowledgement)
                    .mapError(error -> {
                        final MqttSubscribeException result;
                        if (error instanceof Mqtt5SubAckException mqtt5SubAckException) {
                            result = new AllSubscriptionsFailedException(
                                    getFailedSubscriptionStatuses(
                                            GenericMqttSubAck.ofMqtt5SubAck(mqtt5SubAckException.getMqttMessage()),
                                            getSubscriptionTopicFilters(mqttSubscribe)
                                    ),
                                    mqtt5SubAckException
                            );
                        } else {
                            result = new MqttSubscribeException(error);
                        }
                        return result;
                    })
                    .mapBoth(GenericMqttPublish::ofMqtt5Publish, GenericMqttSubAck::ofMqtt5SubAck);
        }

    }

}
