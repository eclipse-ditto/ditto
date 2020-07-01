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
package org.eclipse.ditto.services.connectivity.messaging;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.httppush.HttpPushClientActor;
import org.eclipse.ditto.services.connectivity.messaging.kafka.DefaultKafkaPublisherActorFactory;
import org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt3ClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt5ClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    private DefaultClientActorPropsFactory() {
        super();
    }

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}.
     *
     * @return the factory instance.
     */
    public static DefaultClientActorPropsFactory getInstance() {
        return new DefaultClientActorPropsFactory();
    }

    @Override
    public Props getActorPropsForType(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor) {
        final ConnectionType connectionType = connection.getConnectionType();

        final Props result;
        switch (connectionType) {
            case AMQP_091:
                result = RabbitMQClientActor.props(connection, proxyActor, connectionActor);
                break;
            case AMQP_10:
                result = AmqpClientActor.props(connection, proxyActor, connectionActor);
                break;
            case MQTT:
                result = HiveMqtt3ClientActor.props(connection, proxyActor, connectionActor);
                break;
            case MQTT_5:
                result = HiveMqtt5ClientActor.props(connection, proxyActor, connectionActor);
                break;
            case KAFKA:
                result = KafkaClientActor.props(connection, proxyActor, connectionActor,
                        DefaultKafkaPublisherActorFactory.getInstance());
                break;
            case HTTP_PUSH:
                result = HttpPushClientActor.props(connection, connectionActor);
                break;
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
        return result;
    }

}
