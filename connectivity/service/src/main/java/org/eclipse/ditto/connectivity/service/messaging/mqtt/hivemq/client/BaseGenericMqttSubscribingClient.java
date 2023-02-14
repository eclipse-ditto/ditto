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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscription;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3RxClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt3.message.unsubscribe.Mqtt3Unsubscribe;
import com.hivemq.client.mqtt.mqtt5.Mqtt5RxClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleTransformer;
import io.reactivex.subjects.SingleSubject;

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

    @Override
    public Single<GenericMqttSubAck> subscribe(final GenericMqttSubscribe genericMqttSubscribe) {
        // Error handling is already done by implementations of BaseGenericMqttConnectingClient.
        return sendSubscribe(mqttClient, checkNotNull(genericMqttSubscribe, "genericMqttSubscribe"))
                .compose(handleFailedSubscriptions(genericMqttSubscribe));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public CompletionStage<Void> unsubscribe(final MqttTopicFilter... mqttTopicFilters) {
        final var completable = sendUnsubscribe(mqttClient, mqttTopicFilters);
        final CompletableFuture<Void> result = new CompletableFuture<>();
        completable.subscribe(() -> result.complete(null), result::completeExceptionally);
        return result;
    }

    protected abstract Single<GenericMqttSubAck> sendSubscribe(C mqttClient, GenericMqttSubscribe genericMqttSubscribe);

    abstract Completable sendUnsubscribe(C mqtt3RxClient, MqttTopicFilter... mqttTopicFilters);

    private static SingleTransformer<GenericMqttSubAck, GenericMqttSubAck> handleFailedSubscriptions(
            final GenericMqttSubscribe genericMqttSubscribe
    ) {
        return upstream -> {
            final Single<GenericMqttSubAck> result;
            final var singleSubject = SingleSubject.<GenericMqttSubAck>create();
            upstream.subscribe(singleSubject);
            if (singleSubject.hasValue()) {
                final var genericMqttSubAck = singleSubject.getValue();
                final var failedSubscriptions = getFailedSubscriptionStatuses(genericMqttSubAck,
                        getSubscriptionTopicFilters(genericMqttSubscribe));
                if (failedSubscriptions.isEmpty()) {
                    result = Single.just(genericMqttSubAck);
                } else {

                    /*
                     * The assumption that only some subscriptions failed is
                     * correct here.
                     * If all subscriptions failed then, according to
                     * HiveMQ API doc, the stream would have failed which would
                     * be handled in branch "onErrorResumeNext" in the specific
                     * protocol version client implementation.
                     */
                    result = Single.error(new SomeSubscriptionsFailedException(failedSubscriptions));
                }
            } else if (singleSubject.hasThrowable()) {
                result = Single.error(singleSubject.getThrowable());
            } else {

                // When can this case even happen?
                result = upstream;
            }
            return result;
        };
    }

    private static List<MqttTopicFilter> getSubscriptionTopicFilters(final GenericMqttSubscribe genericMqttSubscribe) {
        return genericMqttSubscribe.genericMqttSubscriptions()
                .map(GenericMqttSubscription::getMqttTopicFilter)
                .toList();
    }

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
    public Flowable<GenericMqttPublish> consumeSubscribedPublishesWithManualAcknowledgement() {
        return consumeIncomingPublishes(mqttClient, MqttGlobalPublishFilter.SUBSCRIBED, true);
    }

    protected abstract Flowable<GenericMqttPublish> consumeIncomingPublishes(C mqttClient,
            MqttGlobalPublishFilter filter,
            boolean manualAcknowledgement);

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
        protected Single<GenericMqttSubAck> sendSubscribe(final Mqtt3RxClient mqtt3RxClient,
                final GenericMqttSubscribe genericMqttSubscribe) {

            return mqtt3RxClient.subscribe(genericMqttSubscribe.getAsMqtt3Subscribe())
                    .map(GenericMqttSubAck::ofMqtt3SubAck)
                    .onErrorResumeNext(error -> {
                        final Single<GenericMqttSubAck> result;
                        if (error instanceof Mqtt3SubAckException mqtt3SubAckException) {
                            result = Single.error(new AllSubscriptionsFailedException(
                                    getFailedSubscriptionStatuses(
                                            GenericMqttSubAck.ofMqtt3SubAck(mqtt3SubAckException.getMqttMessage()),
                                            getSubscriptionTopicFilters(genericMqttSubscribe)
                                    ),
                                    mqtt3SubAckException
                            ));
                        } else {
                            result = Single.error(new MqttSubscribeException(error));
                        }
                        return result;
                    });
        }


        @Override
        Completable sendUnsubscribe(final Mqtt3RxClient mqtt3RxClient, final MqttTopicFilter... mqttTopicFilters) {
            if (mqttTopicFilters.length == 0) {
                return Completable.complete();
            } else {
                final var unsubscribe =
                        Mqtt3Unsubscribe.builder().addTopicFilters(mqttTopicFilters).build();
                return mqtt3RxClient.unsubscribe(unsubscribe);
            }
        }

        @Override
        protected Flowable<GenericMqttPublish> consumeIncomingPublishes(final Mqtt3RxClient mqtt3RxClient,
                final MqttGlobalPublishFilter filter,
                final boolean manualAcknowledgement) {

            return mqtt3RxClient.publishes(filter, manualAcknowledgement).map(GenericMqttPublish::ofMqtt3Publish);
        }

    }

    private static final class Mqtt5RxSubscribingClient extends BaseGenericMqttSubscribingClient<Mqtt5RxClient> {

        private Mqtt5RxSubscribingClient(final Mqtt5RxClient mqtt5RxClient,
                final GenericMqttConnectableClient genericMqttConnectingClient,
                final ClientRole clientRole) {

            super(mqtt5RxClient, genericMqttConnectingClient, clientRole);
        }

        @Override
        protected Single<GenericMqttSubAck> sendSubscribe(final Mqtt5RxClient mqtt5RxClient,
                final GenericMqttSubscribe genericMqttSubscribe) {

            return mqtt5RxClient.subscribe(genericMqttSubscribe.getAsMqtt5Subscribe())
                    .map(GenericMqttSubAck::ofMqtt5SubAck)
                    .onErrorResumeNext(error -> {
                        final Single<GenericMqttSubAck> result;
                        if (error instanceof Mqtt5SubAckException mqtt5SubAckException) {
                            result = Single.error(new AllSubscriptionsFailedException(
                                    getFailedSubscriptionStatuses(
                                            GenericMqttSubAck.ofMqtt5SubAck(mqtt5SubAckException.getMqttMessage()),
                                            getSubscriptionTopicFilters(genericMqttSubscribe)
                                    ),
                                    mqtt5SubAckException
                            ));
                        } else {
                            result = Single.error(new MqttSubscribeException(error));
                        }
                        return result;
                    });
        }

        @Override
        Completable sendUnsubscribe(final Mqtt5RxClient mqtt5RxClient, final MqttTopicFilter... mqttTopicFilters) {
            final var unsubscribe =
                    Mqtt5Unsubscribe.builder().addTopicFilters(mqttTopicFilters).build();
            return mqtt5RxClient.unsubscribe(unsubscribe).ignoreElement();
        }

        @Override
        protected Flowable<GenericMqttPublish> consumeIncomingPublishes(final Mqtt5RxClient mqtt5RxClient,
                final MqttGlobalPublishFilter filter,
                final boolean manualAcknowledgement) {

            return mqtt5RxClient.publishes(filter, manualAcknowledgement).map(GenericMqttPublish::ofMqtt5Publish);
        }

    }

}
