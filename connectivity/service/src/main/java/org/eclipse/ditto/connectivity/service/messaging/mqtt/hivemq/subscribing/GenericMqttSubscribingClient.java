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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttClient;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnAckStatus;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.HiveMqttClientProperties;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.MqttClientConnectException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publish.GenericMqttPublish;

import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import akka.NotUsed;
import akka.stream.javadsl.Source;
import io.reactivex.Single;

/**
 * Generic client for subscribing to topics at the MQTT broker.
 */
public abstract class GenericMqttSubscribingClient implements GenericMqttClient {

    private GenericMqttSubscribingClient() {
        super();
    }

    /**
     * Returns a new instance of {@code GenericMqttSubscribingClient} for the specified {@code HiveMqttClientProperties}
     * argument.
     *
     * @param hiveMqttClientProperties properties which are required for creating a subscribing client.
     * @return the new instance.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    public static GenericMqttSubscribingClient newInstance(final HiveMqttClientProperties hiveMqttClientProperties) {
        checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");

        final GenericMqttSubscribingClient result;
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        final var clientIdentifierFactory = MqttSubscribingClientIdentifierFactory.newInstance(
                hiveMqttClientProperties.getMqttSpecificConfig(),
                mqttConnection,
                hiveMqttClientProperties.getActorUuid()
        );
        if (ConnectionType.MQTT == mqttConnection.getConnectionType()) {
            final var mqtt3Client = HiveMqttClientFactory.getMqtt3Client(hiveMqttClientProperties,
                    clientIdentifierFactory.getMqttClientIdentifier(),
                    true);
            result = ofMqtt3RxClient(mqtt3Client.toRx());
        } else {
            final var mqtt5Client = HiveMqttClientFactory.getMqtt5Client(hiveMqttClientProperties,
                    clientIdentifierFactory.getMqttClientIdentifier(),
                    true);
            result = ofMqtt5RxClient(mqtt5Client.toRx());
        }
        return result;
    }

    /**
     * Returns an instance of {@code GenericMqttSubscribingClient} that operates on the specified {@code Mqtt3RxClient}
     * argument.
     *
     * @param mqtt3RxClient the MQTT client for subscribing to topics.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt3RxClient} is {@code null}.
     */
    static GenericMqttSubscribingClient ofMqtt3RxClient(final Mqtt3RxClient mqtt3RxClient) {
        return new Mqtt3RxSubscribingClient(mqtt3RxClient);
    }

    /**
     * Returns an instance of {@code GenericMqttSubscribingClient} that operates on the specified {@code Mqtt5RxClient}
     * argument.
     *
     * @param mqtt5RxClient the MQTT client for subscribing to topics.
     * @return the instance.
     * @throws NullPointerException if {@code mqtt5RxClient} is {@code null}.
     */
    static GenericMqttSubscribingClient ofMqtt5RxClient(final Mqtt5RxClient mqtt5RxClient) {
        return new Mqtt5RxSubscribingClient(mqtt5RxClient);
    }

    Source<SubscribeResult, NotUsed> subscribe(final GenericMqttSubscribe genericMqttSubscribe) {
        return Source.fromPublisher(sendSubscribe(checkNotNull(genericMqttSubscribe, "genericMqttSubscribe"))
                .map(genericMqttSubAck -> getPossibleSubscribeSuccessResult(genericMqttSubAck, genericMqttSubscribe))
                .onErrorReturn(error -> getTotalSubscribeFailureResult(error, genericMqttSubscribe))
                .toFlowable());
    }

    protected abstract Single<GenericMqttSubAck> sendSubscribe(GenericMqttSubscribe genericMqttSubscribe);

    private SubscribeResult getPossibleSubscribeSuccessResult(final GenericMqttSubAck genericMqttSubAck,
            final GenericMqttSubscribe genericMqttSubscribe) {

        final SubscribeResult result;

        // Some subscriptions may have succeeded and some may have failed or all subscriptions have succeeded.
        final var failedSubscriptions = getFailedSubscriptionStatuses(genericMqttSubAck,
                getSubscriptionTopicFilters(genericMqttSubscribe));
        if (failedSubscriptions.isEmpty()) {
            result = SubscribeSuccess.newInstance(
                    genericMqttSubscribe.genericMqttSubscriptions()
                            .map(GenericMqttSubscription::getMqttTopicFilter)
                            .toList(),
                    consumeSubscribedPublishesWithManualAcknowledgement()
            );
        } else {

            /*
             * The assumption that only some subscriptions is correct here.
             * If all subscriptions failed then, according to HiveMQ API doc,
             * the stream would have failed which would be handled in branch
             * "onErrorReturn".
             */
            result = SubscribeFailure.newInstance(new SomeSubscriptionsFailedException(failedSubscriptions));
        }
        return result;
    }

    private static List<MqttTopicFilter> getSubscriptionTopicFilters(final GenericMqttSubscribe genericMqttSubscribe) {
        return genericMqttSubscribe.genericMqttSubscriptions()
                .map(GenericMqttSubscription::getMqttTopicFilter)
                .collect(Collectors.toList());
    }

    private static List<SubscriptionStatus> getFailedSubscriptionStatuses(
            final GenericMqttSubAck genericMqttSubAck,
            final List<MqttTopicFilter> subscriptionTopicFilters
    ) {
        return ListZipper.zipLists(subscriptionTopicFilters, genericMqttSubAck.genericMqttSubAckReasons())
                .filter(zip -> zip.b().isError())
                .map(zip -> new SubscriptionStatus(zip.a(), zip.b()))
                .collect(Collectors.toList());
    }

    protected abstract Source<GenericMqttPublish, NotUsed> consumeSubscribedPublishesWithManualAcknowledgement();

    private static SubscribeResult getTotalSubscribeFailureResult(final Throwable failure,
            final GenericMqttSubscribe genericMqttSubscribe) {

        final SubscribeFailure result;
        if (failure instanceof Mqtt3SubAckException mqtt3SubAckException) {
            result = SubscribeFailure.newInstance(
                    new AllSubscriptionsFailedException(getFailedSubscriptionStatuses(
                            GenericMqttSubAck.ofMqtt3SubAck(mqtt3SubAckException.getMqttMessage()),
                            getSubscriptionTopicFilters(genericMqttSubscribe)
                    ))
            );
        } else if (failure instanceof Mqtt5SubAckException mqtt5SubAckException) {
            result = SubscribeFailure.newInstance(
                    new AllSubscriptionsFailedException(getFailedSubscriptionStatuses(
                            GenericMqttSubAck.ofMqtt5SubAck(mqtt5SubAckException.getMqttMessage()),
                            getSubscriptionTopicFilters(genericMqttSubscribe)
                    ))
            );
        } else {
            result = SubscribeFailure.newInstance(new MqttSubscribeException(failure));
        }
        return result;
    }

    private static final class Mqtt3RxSubscribingClient extends GenericMqttSubscribingClient {

        private final Mqtt3RxClient mqtt3RxClient;

        private Mqtt3RxSubscribingClient(final Mqtt3RxClient mqtt3RxClient) {
            this.mqtt3RxClient = checkNotNull(mqtt3RxClient, "mqtt3RxClient");
        }

        @Override
        public CompletionStage<GenericMqttConnAckStatus> connect(final GenericMqttConnect genericMqttConnect) {
            checkNotNull(genericMqttConnect, "genericMqttConnect");
            final var mqtt3AsyncClient = mqtt3RxClient.toAsync();
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
            final var mqtt3AsyncClient = mqtt3RxClient.toAsync();
            return mqtt3AsyncClient.disconnect();
        }

        @Override
        protected Single<GenericMqttSubAck> sendSubscribe(final GenericMqttSubscribe genericMqttSubscribe) {
            return mqtt3RxClient.subscribe(genericMqttSubscribe.getAsMqtt3Subscribe())
                    .map(GenericMqttSubAck::ofMqtt3SubAck);
        }

        @Override
        protected Source<GenericMqttPublish, NotUsed> consumeSubscribedPublishesWithManualAcknowledgement() {
            return Source.fromPublisher(mqtt3RxClient.publishes(MqttGlobalPublishFilter.SUBSCRIBED, true))
                    .map(GenericMqttPublish::ofMqtt3Publish);
        }

    }

    private static final class Mqtt5RxSubscribingClient extends GenericMqttSubscribingClient {

        private final Mqtt5RxClient mqtt5RxClient;

        private Mqtt5RxSubscribingClient(final Mqtt5RxClient mqtt5RxClient) {
            this.mqtt5RxClient = checkNotNull(mqtt5RxClient, "mqtt5RxClient");
        }

        @Override
        public CompletionStage<GenericMqttConnAckStatus> connect(final GenericMqttConnect genericMqttConnect) {
            checkNotNull(genericMqttConnect, "genericMqttConnect");
            final var mqtt5AsyncClient = mqtt5RxClient.toAsync();
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
            final var mqtt5AsyncClient = mqtt5RxClient.toAsync();
            return mqtt5AsyncClient.disconnect();
        }

        @Override
        protected Single<GenericMqttSubAck> sendSubscribe(final GenericMqttSubscribe genericMqttSubscribe) {
            return mqtt5RxClient.subscribe(genericMqttSubscribe.getAsMqtt5Subscribe())
                    .map(GenericMqttSubAck::ofMqtt5SubAck);
        }

        @Override
        protected Source<GenericMqttPublish, NotUsed> consumeSubscribedPublishesWithManualAcknowledgement() {
            return Source.fromPublisher(mqtt5RxClient.publishes(MqttGlobalPublishFilter.SUBSCRIBED, true))
                    .map(GenericMqttPublish::ofMqtt5Publish);
        }

    }

    private static final class GenericMqttSubAck {

        private final List<GenericMqttSubAckStatus> genericMqttSubAckReasons;

        private GenericMqttSubAck(final Stream<GenericMqttSubAckStatus> genericMqttSubAckReasons) {
            this.genericMqttSubAckReasons = genericMqttSubAckReasons.toList();
        }

        static GenericMqttSubAck ofMqtt3SubAck(final Mqtt3SubAck mqtt3SubAck) {
            checkNotNull(mqtt3SubAck, "mqtt3SubAck");
            final var returnCodes = mqtt3SubAck.getReturnCodes();
            return new GenericMqttSubAck(returnCodes.stream().map(GenericMqttSubAckStatus::ofMqtt3SubAckReturnCode));
        }

        static GenericMqttSubAck ofMqtt5SubAck(final Mqtt5SubAck mqtt5SubAck) {
            checkNotNull(mqtt5SubAck, "mqtt5SubAck");
            final var reasonCodes = mqtt5SubAck.getReasonCodes();
            return new GenericMqttSubAck(reasonCodes.stream().map(GenericMqttSubAckStatus::ofMqtt5SubAckReasonCode));
        }

        List<GenericMqttSubAckStatus> genericMqttSubAckReasons() {
            return genericMqttSubAckReasons;
        }

        @Override
        public boolean equals(@Nullable final Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            final var that = (GenericMqttSubAck) obj;
            return Objects.equals(genericMqttSubAckReasons, that.genericMqttSubAckReasons);
        }

        @Override
        public int hashCode() {
            return Objects.hash(genericMqttSubAckReasons);
        }

    }

}
