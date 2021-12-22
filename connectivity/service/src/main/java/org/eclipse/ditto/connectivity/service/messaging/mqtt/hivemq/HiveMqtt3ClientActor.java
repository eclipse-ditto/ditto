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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Sink;

/**
 * Actor which handles connection to MQTT 3.1.1 server.
 */
public final class HiveMqtt3ClientActor
        extends AbstractMqttClientActor<Mqtt3Subscribe, Mqtt3Publish, Mqtt3AsyncClient, Mqtt3SubAck> {

    private final HiveMqtt3ClientFactory clientFactory;

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final HiveMqtt3ClientFactory clientFactory,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        this.clientFactory = clientFactory;
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt3ClientActor(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        clientFactory = DefaultHiveMqtt3ClientFactory.getInstance(this::getSshTunnelState);
    }

    @Override
    HiveMqttClientFactory<Mqtt3AsyncClient, ?> getClientFactory() {
        return clientFactory;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param clientFactory factory used to create required mqtt clients
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @param connectivityConfigOverwrites the overwrites for the connectivity config for the given connection.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor, final HiveMqtt3ClientFactory clientFactory,
            final DittoHeaders dittoHeaders, final Config connectivityConfigOverwrites) {
        return Props.create(HiveMqtt3ClientActor.class, connection, proxyActor, connectionActor, clientFactory,
                dittoHeaders, connectivityConfigOverwrites);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        return Props.create(HiveMqtt3ClientActor.class, connection, proxyActor, connectionActor, dittoHeaders,
                connectivityConfigOverwrites);
    }

    @Override
    AbstractMqttSubscriptionHandler<Mqtt3Subscribe, Mqtt3Publish, Mqtt3SubAck> createSubscriptionHandler(
            final Connection connection, final Mqtt3AsyncClient client, final ThreadSafeDittoLoggingAdapter logger) {

        return new HiveMqtt3SubscriptionHandler(connection, client, logger);
    }

    @Override
    CompletionStage<Mqtt3ConnAck> sendConn(final Mqtt3AsyncClient client, final boolean cleanSession,
            @Nullable final Duration keepAliveInterval) {
        final Mqtt3ConnectBuilder.Send<CompletableFuture<Mqtt3ConnAck>> connectWith = client.connectWith();
        if (keepAliveInterval != null) {
            connectWith.keepAlive((int) keepAliveInterval.getSeconds());
        }
        return connectWith.cleanSession(cleanSession).send();
    }

    @Override
    CompletionStage<Void> disconnectClient(final Mqtt3AsyncClient client) {
        return client.disconnect();
    }

    @Override
    ActorRef startPublisherActor(final Connection connection, final Mqtt3AsyncClient client) {
        final Props publisherActorProps = HiveMqtt3PublisherActor.props(connection,
                client,
                isDryRun(),
                getDefaultClientId(),
                connectivityStatusResolver,
                connectivityConfig());
        return startChildActorConflictFree(HiveMqtt3PublisherActor.NAME, publisherActorProps);
    }

    @Override
    ActorRef startConsumerActor(final boolean dryRun,
            final Source source,
            final Sink<Object, NotUsed> inboundMappingSink,
            final MqttSpecificConfig specificConfig) {

        return startChildActorConflictFree(HiveMqtt3ConsumerActor.NAME,
                HiveMqtt3ConsumerActor.props(connection(), inboundMappingSink, source, dryRun, specificConfig,
                        connectivityStatusResolver, connectivityConfig()));
    }

}
