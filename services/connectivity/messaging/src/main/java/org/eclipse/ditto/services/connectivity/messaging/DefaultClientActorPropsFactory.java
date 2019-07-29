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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.kafka.DefaultKafkaPublisherActorFactory;
import org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.alpakka.MqttClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq.HiveMqtt3ClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    private final ConnectionConfig connectionConfig;

    private DefaultClientActorPropsFactory(
            final ConnectionConfig connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}.
     *
     * @param connectionConfig the connection config used for providing client actors.
     * @return the factory instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultClientActorPropsFactory getInstance(
            final ConnectionConfig connectionConfig) {
        return new DefaultClientActorPropsFactory(connectionConfig);
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef conciergeForwarder) {
        final ConnectionType connectionType = connection.getConnectionType();
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQClientActor.props(connection, conciergeForwarder);
            case AMQP_10:
                return AmqpClientActor.props(connection, conciergeForwarder);
            case MQTT:
                if (connectionConfig.getMqttConfig().isExperimental()) {
                    return HiveMqtt3ClientActor.props(connection, conciergeForwarder);
                }
                return MqttClientActor.props(connection, conciergeForwarder);
            case KAFKA:
                return KafkaClientActor.props(connection, conciergeForwarder,
                        DefaultKafkaPublisherActorFactory.getInstance());
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }

}
