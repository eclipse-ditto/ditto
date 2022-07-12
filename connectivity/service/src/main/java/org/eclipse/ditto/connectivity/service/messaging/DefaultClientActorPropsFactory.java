/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.connectivity.service.messaging.httppush.HttpPushClientActor;
import org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.MqttClientActor;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.GenericMqttClientFactory;
import org.eclipse.ditto.connectivity.service.messaging.rabbitmq.RabbitMQClientActor;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}. Singleton which is created just once
 * and otherwise returns the already created instance.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    @Nullable private static DefaultClientActorPropsFactory instance;

    private DefaultClientActorPropsFactory() {}

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}. Creates a new one if not already done.
     *
     * @return the factory instance.
     */
    public static DefaultClientActorPropsFactory getInstance() {
        if (null == instance) {
            instance = new DefaultClientActorPropsFactory();
        }
        return instance;
    }

    @Override
    public Props getActorPropsForType(final Connection connection,
            final ActorRef proxyActor,
            final ActorRef connectionActor,
            final ActorSystem actorSystem,
            final DittoHeaders dittoHeaders,
            final Config connectivityConfigOverwrites) {

        return switch (connection.getConnectionType()) {
            case AMQP_091 -> RabbitMQClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case AMQP_10 -> AmqpClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    connectivityConfigOverwrites,
                    actorSystem,
                    dittoHeaders);
            case MQTT, MQTT_5 -> MqttClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites,
                    GenericMqttClientFactory.newInstance());
            case KAFKA -> KafkaClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
            case HTTP_PUSH -> HttpPushClientActor.props(connection,
                    proxyActor,
                    connectionActor,
                    dittoHeaders,
                    connectivityConfigOverwrites);
        };
    }

}
