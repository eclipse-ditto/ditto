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
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;

import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Actor which handles connection to MQTT 5 server.
 *
 * @since 1.1.0
 */
public final class HiveMqtt5ClientActor
        extends AbstractMqttClientActor<Mqtt5Subscribe, Mqtt5Publish, Mqtt5AsyncClient, Mqtt5SubAck> {

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt5ClientActor(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final HiveMqtt5ClientFactory clientFactory) {

        super(connection, proxyActor, connectionActor, clientFactory);
    }

    @SuppressWarnings("unused") // used by `props` via reflection
    private HiveMqtt5ClientActor(final Connection connection, final ActorRef proxyActor,
            final ActorRef connectionActor) {
        this(connection, proxyActor, connectionActor, DefaultHiveMqtt5ClientFactory.getInstance());
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param clientFactory factory used to create required mqtt clients
     * @param connectionActor the parent connection actor
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, final ActorRef proxyActor,
            final HiveMqtt5ClientFactory clientFactory, final ActorRef connectionActor) {
        return Props.create(HiveMqtt5ClientActor.class, connection, proxyActor, connectionActor, clientFactory);
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the parent connection actor.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor) {
        return Props.create(HiveMqtt5ClientActor.class, connection, proxyActor, connectionActor);
    }

    @Override
    AbstractMqttSubscriptionHandler<Mqtt5Subscribe, Mqtt5Publish, Mqtt5SubAck> createSubscriptionHandler(
            final Connection connection, final Mqtt5AsyncClient client, final ThreadSafeDittoLoggingAdapter logger) {

        return new HiveMqtt5SubscriptionHandler(connection, client, logger);
    }

    @Override
    CompletionStage<?> sendConn(final Mqtt5AsyncClient client, final boolean cleanSession) {
        return client.connectWith().cleanStart(cleanSession).send();
    }

    @Override
    CompletionStage<Void> disconnectClient(final Mqtt5AsyncClient client) {
        return client.disconnect();
    }

    @Override
    ActorRef startPublisherActor(final Connection connection, final Mqtt5AsyncClient client) {
        final Props publisherActorProps =
                HiveMqtt5PublisherActor.props(connection, client, isDryRun(), getDefaultClientId());
        return startChildActorConflictFree(HiveMqtt5PublisherActor.NAME, publisherActorProps);
    }

    @Override
    ActorRef startConsumerActor(final boolean dryRun,
            final Source source,
            final ActorRef inboundMessageProcessor,
            final MqttSpecificConfig specificConfig) {

        return startChildActorConflictFree(HiveMqtt5ConsumerActor.NAME,
                HiveMqtt5ConsumerActor.props(connectionId(), inboundMessageProcessor, source, dryRun, specificConfig));
    }

}
