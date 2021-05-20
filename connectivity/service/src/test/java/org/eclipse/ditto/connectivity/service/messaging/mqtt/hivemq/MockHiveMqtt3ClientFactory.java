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
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientConfig;
import com.hivemq.client.mqtt.mqtt3.lifecycle.Mqtt3ClientConnectedContext;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;

import akka.actor.ActorRef;

/**
 * Mocks an MQTT connection with HiveMq client.
 */
class MockHiveMqtt3ClientFactory implements HiveMqtt3ClientFactory {

    private Exception connectException = null;
    private final Map<String, List<Mqtt3Publish>> messages = new HashMap<>();
    private ActorRef testProbe;
    private CompletableFuture<Mqtt3SubAck> subscribeFuture =
            CompletableFuture.completedFuture(mock(Mqtt3SubAck.class));

    private final List<Mqtt3AsyncClient> clients = new LinkedList<>();

    MockHiveMqtt3ClientFactory withException(final Exception connectException) {
        this.connectException = connectException;
        return this;
    }

    private MockHiveMqtt3ClientFactory withMessage(final Mqtt3Publish message) {
        messages.compute(message.getTopic().toString(), (key, value) -> {
            final List<Mqtt3Publish> msgs = (value == null) ? new LinkedList<>() : value;
            msgs.add(message);
            return msgs;
        });
        return this;
    }

    MockHiveMqtt3ClientFactory withMessages(final List<Mqtt3Publish> messages) {
        messages.forEach(this::withMessage);
        return this;
    }

    MockHiveMqtt3ClientFactory withTestProbe(final ActorRef testProbe) {
        this.testProbe = testProbe;
        return this;
    }

    MockHiveMqtt3ClientFactory withFailingSubscribe() {
        final CompletableFuture<Mqtt3SubAck> subscribeFuture = new CompletableFuture<>();
        subscribeFuture.completeExceptionally(new IllegalStateException("you shall not pass"));
        this.subscribeFuture = subscribeFuture;
        return this;
    }

    void expectDisconnectCalled() {
        // disconnect may be called multiple times, just be sure connection is closed
        clients.forEach(c -> verify(c, timeout(500).atLeastOnce()).disconnect());
    }

    @Override
    public Mqtt3AsyncClient newClient(final Connection connection,
            final String identifier,
            final MqttConfig mqttConfig,
            final MqttSpecificConfig mqttSpecificConfig,
            final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger) {

        final Mqtt3AsyncClient client = mock(Mqtt3AsyncClient.class);
        when(client.toAsync()).thenReturn(client);
        final Mqtt3ConnectBuilder.Send<?> send = mock(Mqtt3ConnectBuilder.Send.class, RETURNS_SELF);
        final CompletableFuture<Mqtt3ConnAck> connectFuture = new CompletableFuture<>();

        if (connectException != null) {
            connectFuture.completeExceptionally(connectException);
        } else {
            connectFuture.complete(mock(Mqtt3ConnAck.class));
        }

        final CompletableFuture<Void> disconnectFuture = CompletableFuture.completedFuture(null);

        // mock connect
        when(client.connectWith()).thenAnswer(params -> send);
        when(send.send()).then(invocation -> {
            if (connectedListener != null) {
                final Mqtt3ClientConnectedContext mock = mock(Mqtt3ClientConnectedContext.class);
                when(mock.getClientConfig()).thenReturn(mock(Mqtt3ClientConfig.class));
                connectedListener.onConnected(mock);
            }

            // remember which clients are connected to verify disconnect later
            clients.add(client);

            return connectFuture;
        });

        // mock disconnect
        when(client.disconnect()).thenReturn(disconnectFuture);

        // mock subscribe
        when(client.subscribe(any(Mqtt3Subscribe.class), any(Consumer.class), anyBoolean())).thenAnswer(i -> {
            if (!subscribeFuture.isCompletedExceptionally()) {
                // try to send messages for this topic
                final Mqtt3Subscribe sub = i.getArgument(0);
                final Consumer<Mqtt3Publish> consumer = i.getArgument(1);

                // wait for conn future to complete before sending PUBLISH messages
                connectFuture.thenAccept(connAck -> sub.getSubscriptions().forEach(s -> {
                    final MqttTopicFilter topicFilter = s.getTopicFilter();

                    messages.entrySet().stream()
                            .filter(e -> topicFilter.matches(MqttTopic.of(e.getKey())))
                            .flatMap(e -> e.getValue().stream())
                            .forEach(m -> ForkJoinPool.commonPool().execute(() -> consumer.accept(m)));
                }));
            }
            return subscribeFuture;
        });

        // mock publish
        when(client.publish(any(Mqtt3Publish.class))).thenAnswer(i -> {
            final Mqtt3Publish mqtt3Publish = i.getArgument(0);
            if (testProbe != null) {
                testProbe.tell(mqtt3Publish, ActorRef.noSender());
            }
            return CompletableFuture.completedFuture(mqtt3Publish);
        });

        return client;
    }

    @Override
    public Mqtt3ClientBuilder newClientBuilder(final Connection connection,
            final String identifier,
            final MqttConfig mqttConfig,
            final MqttSpecificConfig mqttSpecificConfig,
            final boolean applyLastWillConfig,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener,
            final ConnectionLogger connectionLogger) {
        final Mqtt3Client client =
                newClient(connection, identifier, mqttConfig, mqttSpecificConfig, applyLastWillConfig,
                        connectedListener, disconnectedListener, connectionLogger);
        final Mqtt3ClientBuilder builder = Mockito.mock(Mqtt3ClientBuilder.class);
        Mockito.doReturn(client).when(builder).build();
        return builder;
    }

}
