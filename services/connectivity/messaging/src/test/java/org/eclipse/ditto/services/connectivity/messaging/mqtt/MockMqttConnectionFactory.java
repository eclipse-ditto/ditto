/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MqttSource;

import akka.Done;
import akka.actor.ActorRef;
import akka.japi.function.Predicate;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Mocks an MQTT connection by pre-created streams.
 */
final class MockMqttConnectionFactory implements MqttConnectionFactory {

    static final String COMPLETION_MESSAGE = "MockMqttSink completed.";

    private final Connection connection;
    private final Collection<MqttMessage> messages;
    private final ActorRef testProbe;

    @Nullable
    private final Exception error;

    private MockMqttConnectionFactory(final Connection connection, final Collection<MqttMessage> messages,
            final ActorRef testProbe, @Nullable final Exception error) {

        this.connection = connection;
        this.messages = messages;
        this.testProbe = testProbe;
        this.error = error;
    }

    static BiFunction<Connection, DittoHeaders, MqttConnectionFactory> with(final ActorRef testProbe,
            final MqttMessage... messages) {

        return (connection, headers) ->
                new MockMqttConnectionFactory(connection, Arrays.asList(messages), testProbe, null);
    }

    static BiFunction<Connection, DittoHeaders, MqttConnectionFactory> withError(final ActorRef testProbe,
            final Exception error) {
        return (connection, headers) ->
                new MockMqttConnectionFactory(connection, Collections.emptyList(), testProbe, error);
    }

    @Override
    public String connectionId() {
        return connection.getId();
    }

    @Override
    public Source<MqttMessage, CompletionStage<Done>> newSource(final MqttSource mqttSource,
            final int bufferSize) {
        if (error != null) {
            return Source.<MqttMessage>failed(error).mapMaterializedValue(this::failedFuture);
        } else {
            return Source.from(messages)
                    .filter(MockMqttConnectionFactory.topicMatches(mqttSource.getAddresses()))
                    .mapMaterializedValue(whatever -> CompletableFuture.completedFuture(Done.getInstance()));
        }
    }

    @Override
    public Sink<MqttMessage, CompletionStage<Done>> newSink() {
        if (error != null) {
            return Flow.<MqttMessage, Object>fromFunction(x -> x).to(Sink.ignore())
                    .mapMaterializedValue(this::failedFuture);
        } else {
            return Flow.<MqttMessage, Object>fromFunction(x -> x).to(Sink.actorRef(testProbe, COMPLETION_MESSAGE))
                    .mapMaterializedValue(whatever -> CompletableFuture.completedFuture(Done.getInstance()));
        }
    }

    private <S, T> CompletableFuture<T> failedFuture(final S whatever) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(Optional.ofNullable(error).orElseGet(IllegalStateException::new));
        return future;
    }

    private static Predicate<MqttMessage> topicMatches(final Collection<String> collection) {
        return mqttMessage -> collection.stream().anyMatch(filter -> matchesMqttFilter(filter, mqttMessage.topic()));
    }

    private static boolean matchesMqttFilter(final String filter, final String topic) {
        return topic.matches(filter.replaceAll("\\+", "[^/]*").replaceAll("#", ".*"));
    }
}
