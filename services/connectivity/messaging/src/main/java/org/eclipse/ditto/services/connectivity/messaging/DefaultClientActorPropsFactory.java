/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}.
 */
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    private static final DefaultClientActorPropsFactory INSTANCE = new DefaultClientActorPropsFactory();

    private DefaultClientActorPropsFactory() {
    }

    /**
     * @return factory instance
     */
    public static ClientActorPropsFactory getInstance() {
        return INSTANCE;
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
                return MqttClientActor.props(connection, conciergeForwarder);
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }
}
