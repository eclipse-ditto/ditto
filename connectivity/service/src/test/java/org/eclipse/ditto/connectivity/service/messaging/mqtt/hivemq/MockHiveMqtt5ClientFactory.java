/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientConfig;
import com.hivemq.client.mqtt.mqtt5.advanced.Mqtt5ClientAdvancedConfigBuilder;
import com.hivemq.client.mqtt.mqtt5.advanced.interceptor.Mqtt5ClientInterceptorsBuilder;
import com.hivemq.client.mqtt.mqtt5.lifecycle.Mqtt5ClientConnectedContext;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import akka.actor.ActorRef;

/**
 * Mocks an MQTT connection with HiveMq client.
 */
class MockHiveMqtt5ClientFactory implements HiveMqtt5ClientFactory {

    private Exception connectException = null;
    private final Map<String, List<Mqtt5Publish>> messages = new HashMap<>();
    private ActorRef testProbe;
    private CompletableFuture<Mqtt5SubAck> subscribeFuture =
            CompletableFuture.completedFuture(mock(Mqtt5SubAck.class));

    private final List<Mqtt5AsyncClient> clients = new LinkedList<>();

    MockHiveMqtt5ClientFactory withException(final Exception connectException) {
        this.connectException = connectException;
        return this;
    }

    private MockHiveMqtt5ClientFactory withMessage(final Mqtt5Publish message) {
        messages.compute(message.getTopic().toString(), (key, value) -> {
            final List<Mqtt5Publish> msgs = (value == null) ? new LinkedList<>() : value;
            msgs.add(message);
            return msgs;
        });
        return this;
    }

    MockHiveMqtt5ClientFactory withMessages(final List<Mqtt5Publish> messages) {
        messages.forEach(this::withMessage);
        return this;
    }

    MockHiveMqtt5ClientFactory withTestProbe(final ActorRef testProbe) {
        this.testProbe = testProbe;
        return this;
    }

    MockHiveMqtt5ClientFactory withFailingSubscribe() {
        final CompletableFuture<Mqtt5SubAck> subscribeFuture = new CompletableFuture<>();
        subscribeFuture.completeExceptionally(new IllegalStateException("you shall not pass"));
        this.subscribeFuture = subscribeFuture;
        return this;
    }

    void expectDisconnectCalled() {
        // disconnect may be called multiple times, just be sure connection is closed
        clients.forEach(c -> verify(c, timeout(500).atLeastOnce()).disconnect());
    }

    @Override
    public Mqtt5AsyncClient newClient(final Connection connection,
            final String identifier,
            final MqttConfig mqttConfig,
            final MqttSpecificConfig mqttSpecificConfig,
            final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger,
            final boolean doubleDecodingEnabled) {

        final Mqtt5AsyncClient client = mock(Mqtt5AsyncClient.class);
        doReturn(client).when(client).toAsync();
        final Mqtt5ConnectBuilder.Send send = mock(Mqtt5ConnectBuilder.Send.class, RETURNS_SELF);
        final CompletableFuture<Mqtt5ConnAck> connectFuture = new CompletableFuture<>();

        if (connectException != null) {
            connectFuture.completeExceptionally(connectException);
        } else {
            connectFuture.complete(mock(Mqtt5ConnAck.class));
        }

        final CompletableFuture<Void> disconnectFuture = CompletableFuture.completedFuture(null);


        // mock connect
        when(client.connectWith()).thenReturn(send);
        when(send.send()).then(invocation -> {
            if (connectedListener != null) {
                final Mqtt5ClientConnectedContext mock = mock(Mqtt5ClientConnectedContext.class);
                when(mock.getClientConfig()).thenReturn(mock(Mqtt5ClientConfig.class));
                connectedListener.onConnected(mock);
            }

            // remember which clients are connected to verify disconnect later
            clients.add(client);

            return connectFuture;
        });

        // mock disconnect
        when(client.disconnect()).thenReturn(disconnectFuture);

        // mock subscribe
        when(client.subscribe(any(Mqtt5Subscribe.class), any(Consumer.class), anyBoolean())).thenAnswer(i -> {
            if (!subscribeFuture.isCompletedExceptionally()) {
                // try to send messages for this topic
                final Mqtt5Subscribe sub = i.getArgument(0);
                final Consumer<Mqtt5Publish> consumer = i.getArgument(1);

                sub.getSubscriptions().forEach(s -> {
                    final MqttTopicFilter topicFilter = s.getTopicFilter();

                    messages.entrySet().stream()
                            .filter(e -> topicFilter.matches(MqttTopic.of(e.getKey())))
                            .flatMap(e -> e.getValue().stream())
                            .forEach(m -> ForkJoinPool.commonPool().execute(() -> consumer.accept(m)));
                });
            }
            return subscribeFuture;
        });

        // mock publish
        when(client.publish(any(Mqtt5Publish.class))).thenAnswer(i -> {
            final Mqtt5Publish mqtt5Publish = i.getArgument(0);
            if (testProbe != null) {
                testProbe.tell(mqtt5Publish, ActorRef.noSender());
            }
            return CompletableFuture.completedFuture(mqtt5Publish);
        });

        return client;
    }

    @Override
    public Mqtt5ClientBuilder newClientBuilder(final Connection connection,
            final String identifier,
            final MqttConfig mqttConfig,
            final MqttSpecificConfig mqttSpecificConfig,
            final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger,
            final boolean doubleDecodingEnabled) {

        final Mqtt5Client client =
                newClient(connection, identifier, mqttConfig, mqttSpecificConfig, applyLastWillConfig,
                        connectedListener, disconnectedListener, connectionLogger, doubleDecodingEnabled);
        final Mqtt5ClientBuilder builder = Mockito.mock(Mqtt5ClientBuilder.class);
        final Mqtt5ClientAdvancedConfigBuilder.Nested<Mqtt5ClientBuilder> advancedConfig =
                Mockito.mock(Mqtt5ClientAdvancedConfigBuilder.Nested.class);
        final Mqtt5ClientInterceptorsBuilder.Nested<Mqtt5ClientAdvancedConfigBuilder.Nested<Mqtt5ClientBuilder>>
                interceptors = Mockito.mock(Mqtt5ClientInterceptorsBuilder.Nested.class);
        doReturn(client).when(builder).build();
        doReturn(advancedConfig).when(builder).advancedConfig();
        doReturn(interceptors).when(advancedConfig).interceptors();
        doReturn(interceptors).when(interceptors).incomingQos1Interceptor(any());
        doReturn(interceptors).when(interceptors).incomingQos2Interceptor(any());
        doReturn(advancedConfig).when(interceptors).applyInterceptors();
        doReturn(builder).when(advancedConfig).applyAdvancedConfig();
        return builder;
    }

}
