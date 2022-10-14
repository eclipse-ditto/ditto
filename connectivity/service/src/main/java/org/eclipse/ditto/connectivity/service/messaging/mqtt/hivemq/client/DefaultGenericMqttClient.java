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

import org.eclipse.ditto.connectivity.service.messaging.mqtt.IllegalKeepAliveIntervalSecondsException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect.GenericMqttConnect;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAck;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubscribe;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

import io.reactivex.Flowable;
import io.reactivex.Single;

/**
 * Default implementation of {@link GenericMqttClient}.
 */
final class DefaultGenericMqttClient implements GenericMqttClient {

    private final BaseGenericMqttSubscribingClient<?> subscribingClient;
    private final BaseGenericMqttPublishingClient<?> publishingClient;
    private final HiveMqttClientProperties hiveMqttClientProperties;
    private final DittoLogger logger;

    private DefaultGenericMqttClient(final BaseGenericMqttSubscribingClient<?> subscribingClient,
            final BaseGenericMqttPublishingClient<?> publishingClient,
            final HiveMqttClientProperties hiveMqttClientProperties) {

        this.subscribingClient = subscribingClient;
        this.publishingClient = publishingClient;
        this.hiveMqttClientProperties = hiveMqttClientProperties;
        logger = DittoLoggerFactory.getLogger(DefaultGenericMqttClient.class)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, hiveMqttClientProperties.getConnectionId());
    }

    static GenericMqttClient newInstance(final BaseGenericMqttSubscribingClient<?> genericMqttSubscribingClient,
            final BaseGenericMqttPublishingClient<?> genericMqttPublishingClient,
            final HiveMqttClientProperties hiveMqttClientProperties) {

        return new DefaultGenericMqttClient(checkNotNull(genericMqttSubscribingClient, "genericMqttSubscribingClient"),
                checkNotNull(genericMqttPublishingClient, "genericMqttPublishingClient"),
                checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties"));
    }

    @Override
    public CompletionStage<Void> connect() {
        return getGenericMqttConnect().thenCompose(this::connect);
    }

    private CompletionStage<GenericMqttConnect> getGenericMqttConnect() {
        final var mqttSpecificConfig = hiveMqttClientProperties.getMqttSpecificConfig();
        final var mqttConfig = hiveMqttClientProperties.getMqttConfig();
        try {
            return CompletableFuture.completedFuture(
                    GenericMqttConnect.newInstance(mqttSpecificConfig.cleanSession(),
                            mqttSpecificConfig.getKeepAliveIntervalOrDefault(),
                            mqttConfig.getSessionExpiryInterval(),
                            mqttConfig.getClientReceiveMaximum())
            );
        } catch (final IllegalKeepAliveIntervalSecondsException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletionStage<Void> connect(final GenericMqttConnect genericMqttConnect) {
        checkNotNull(genericMqttConnect, "genericMqttConnect");
        return connectSubscribingClient(genericMqttConnect)
                .thenCompose(unusedVoid -> connectPublishingClient(genericMqttConnect));
    }

    private CompletionStage<Void> connectSubscribingClient(final GenericMqttConnect genericMqttConnect) {
        return subscribingClient.connect(genericMqttConnect)
                .whenComplete((unusedVoid, throwable) -> {
                    if (null == throwable) {
                        logger.debug("Connected subscribing client <{}>.", subscribingClient);
                    } else {
                        logger.info("Failed to connect subscribing client <{}>.", subscribingClient, throwable);
                    }
                });
    }

    private CompletionStage<Void> connectPublishingClient(final GenericMqttConnect genericMqttConnect) {
        return publishingClient.connect(genericMqttConnect)
                .whenComplete((unusedVoid, throwable) -> {
                    if (null == throwable) {
                        logger.debug("Connected publishing client <{}>.", publishingClient);
                    } else {
                        logger.info("Failed to connect publishing client <{}>. Disconnecting subscribing client …",
                                publishingClient,
                                throwable);
                        disconnectSubscribingClient();
                    }
                });
    }

    private CompletableFuture<Void> disconnectSubscribingClient() {
        return subscribingClient.disconnect()
                .whenComplete((unusedVoid, throwable) -> {
                    if (null == throwable) {
                        logger.debug("Disconnected subscribing client <{}>.", subscribingClient);
                    } else {
                        logger.info("Failed to disconnect subscribing client <{}>.", subscribingClient, throwable);
                    }
                })
                .toCompletableFuture();
    }

    @Override
    public CompletionStage<Void> disconnect() {
        return disconnectClientRole(ClientRole.CONSUMER_PUBLISHER);
    }

    @Override
    public CompletionStage<Void> disconnectClientRole(final ClientRole clientRole) {
        return switch (checkNotNull(clientRole, "clientRole")) {
            case CONSUMER -> disconnectSubscribingClient();
            case PUBLISHER -> disconnectPublishingClient();
            case CONSUMER_PUBLISHER ->
                    CompletableFuture.allOf(disconnectPublishingClient(), disconnectSubscribingClient());
        };
    }

    private CompletableFuture<Void> disconnectPublishingClient() {
        return publishingClient.disconnect()
                .whenComplete((unusedVoid, throwable) -> {
                    if (null == throwable) {
                        logger.debug("Disconnected publishing client <{}>.", publishingClient);
                    } else {
                        logger.info("Failed to disconnect publishing client <{}>.", publishingClient);
                    }
                })
                .toCompletableFuture();
    }

    @Override
    public Single<GenericMqttSubAck> subscribe(final GenericMqttSubscribe genericMqttSubscribe) {
        return subscribingClient.subscribe(genericMqttSubscribe);
    }

    @Override
    public Flowable<GenericMqttPublish> consumeSubscribedPublishesWithManualAcknowledgement() {
        return subscribingClient.consumeSubscribedPublishesWithManualAcknowledgement();
    }

    @Override
    public CompletionStage<Void> unsubscribe(final MqttTopicFilter... mqttTopicFilters) {
        return subscribingClient.unsubscribe(mqttTopicFilters);
    }

    @Override
    public CompletionStage<GenericMqttPublishResult> publish(final GenericMqttPublish genericMqttPublish) {
        return publishingClient.publish(genericMqttPublish);
    }

}
