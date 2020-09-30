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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.DiagnosticLoggingAdapter;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class HiveMqtt3ClientActor
        extends AbstractMqttClientActor<Mqtt3Subscribe, Mqtt3Publish, Mqtt3AsyncClient, Mqtt3SubAck> {

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection,
            @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor,
            final HiveMqtt3ClientFactory clientFactory) {

        super(connection, proxyActor, connectionActor, clientFactory);
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor) {
        this(connection, proxyActor, connectionActor, DefaultHiveMqtt3ClientFactory.getInstance());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param clientFactory factory used to create required mqtt clients
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor, final HiveMqtt3ClientFactory clientFactory) {
        return Props.create(HiveMqtt3ClientActor.class, connection, proxyActor, connectionActor, clientFactory);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor) {
        return Props.create(HiveMqtt3ClientActor.class, connection, proxyActor, connectionActor);
    }

    @Override
    AbstractMqttSubscriptionHandler<Mqtt3Subscribe, Mqtt3Publish, Mqtt3SubAck> createSubscriptionHandler(
            final Connection connection, final Mqtt3AsyncClient client, final DiagnosticLoggingAdapter log) {
        return new HiveMqtt3SubscriptionHandler(connection, client, log);
    }

    @Override
    CompletionStage<?> sendConn(final Mqtt3AsyncClient client, final boolean cleanSession) {
        return client.connectWith().cleanSession(cleanSession).send();
    }

    @Override
    CompletionStage<Void> disconnectClient(final Mqtt3AsyncClient client) {
        return client.disconnect();
    }

    @Override
    ActorRef startPublisherActor(final Connection connection, final Mqtt3AsyncClient client) {
        final Props publisherActorProps = HiveMqtt3PublisherActor.props(connection, client, isDryRun());
        return startChildActorConflictFree(HiveMqtt3PublisherActor.NAME, publisherActorProps);
    }

    @Override
    ActorRef startConsumerActor(final boolean dryRun, final Source source, final ActorRef inboundMessageProcessor,
            final MqttSpecificConfig specificConfig) {
        return startChildActorConflictFree(HiveMqtt3ConsumerActor.NAME,
                HiveMqtt3ConsumerActor.props(connectionId(), inboundMessageProcessor, source, dryRun, specificConfig));
    }
}
