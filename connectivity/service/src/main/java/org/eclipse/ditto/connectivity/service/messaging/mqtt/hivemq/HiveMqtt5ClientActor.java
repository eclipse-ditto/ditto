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

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Sink;

/**
 * Actor which handles connection to MQTT 5 server.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5ClientActor
        extends AbstractMqttClientActor<Mqtt5Subscribe, Mqtt5Publish, Mqtt5AsyncClient, Mqtt5SubAck> {

    private final HiveMqtt5ClientFactory clientFactory;

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt5ClientActor(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final HiveMqtt5ClientFactory clientFactory,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        this.clientFactory = clientFactory;
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt5ClientActor(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        super(connection, proxyActor, connectionActor, dittoHeaders, connectivityConfigOverwrites);
        this.clientFactory = DefaultHiveMqtt5ClientFactory.getInstance(this::getSshTunnelState);
    }

    @Override
    HiveMqttClientFactory<Mqtt5AsyncClient, ?> getClientFactory() {
        return clientFactory;
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param clientFactory factory used to create required mqtt clients
     * @param connectionActor the parent connection actor
     * @param dittoHeaders headers of the command that caused this actor to be created
     * @param connectivityConfigOverwrites the overwrites for the connectivity config for the given connection.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef proxyActor,
            final HiveMqtt5ClientFactory clientFactory, final ActorRef connectionActor,
            final DittoHeaders dittoHeaders, final Config connectivityConfigOverwrites) {
        return Props.create(HiveMqtt5ClientActor.class, connection, proxyActor, connectionActor, clientFactory,
                dittoHeaders, connectivityConfigOverwrites);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the parent connection actor.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {
        return Props.create(HiveMqtt5ClientActor.class, connection, proxyActor, connectionActor, dittoHeaders,
                connectivityConfigOverwrites);
    }

    @Override
    AbstractMqttSubscriptionHandler<Mqtt5Subscribe, Mqtt5Publish, Mqtt5SubAck> createSubscriptionHandler(
            final Connection connection, final Mqtt5AsyncClient client, final ThreadSafeDittoLoggingAdapter logger) {

        return new HiveMqtt5SubscriptionHandler(connection, client, logger);
    }

    @Override
    CompletionStage<Mqtt5ConnAck> sendConn(final Mqtt5AsyncClient client, final boolean cleanSession,
            @Nullable final Duration keepAliveInterval) {
        final Mqtt5ConnectBuilder.Send<CompletableFuture<Mqtt5ConnAck>> connectWith = client.connectWith();
        if (keepAliveInterval != null) {
            connectWith.keepAlive((int) keepAliveInterval.getSeconds());
        }
        return connectWith.cleanStart(cleanSession).send();
    }

    @Override
    CompletionStage<Void> disconnectClient(final Mqtt5AsyncClient client) {
        return client.disconnect();
    }

    @Override
    ActorRef startPublisherActor(final Connection connection, final Mqtt5AsyncClient client) {
        final Props publisherActorProps = HiveMqtt5PublisherActor.props(connection,
                client,
                isDryRun(),
                connectivityStatusResolver,
                connectivityConfig());
        return startChildActorConflictFree(HiveMqtt5PublisherActor.NAME, publisherActorProps);
    }

    @Override
    ActorRef startConsumerActor(final boolean dryRun,
            final Source source,
            final Sink<Object, NotUsed> inboundMappingSink,
            final MqttSpecificConfig specificConfig) {

        return startChildActorConflictFree(HiveMqtt5ConsumerActor.NAME,
                HiveMqtt5ConsumerActor.props(connection(), inboundMappingSink, source, dryRun, specificConfig,
                        connectivityStatusResolver, connectivityConfig()));
    }

}
