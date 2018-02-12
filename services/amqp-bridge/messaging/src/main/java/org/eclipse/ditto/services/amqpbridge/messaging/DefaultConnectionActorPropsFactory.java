/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.amqpbridge.messaging;

import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpConnectionActor;
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpConnectionBasedJmsConnectionFactory;
import org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq.RabbitMQConnectionActor;

import akka.actor.ActorRef;
import akka.actor.Props;

public class DefaultConnectionActorPropsFactory implements ConnectionActorPropsFactory {

    private static final AmqpConnectionBasedJmsConnectionFactory JMS_CONNECTION_FACTORY =
            AmqpConnectionBasedJmsConnectionFactory.getInstance();

    private DefaultConnectionActorPropsFactory() {
    }

    public static ConnectionActorPropsFactory getInstance() {
        return new DefaultConnectionActorPropsFactory();
    }

    @Override
    public Props getActorPropsForType(final AmqpConnection amqpConnection, final ActorRef commandProcessor) {
        switch (amqpConnection.getConnectionType()) {
            case AMQP_091:
                return RabbitMQConnectionActor.props(amqpConnection, commandProcessor);
            case AMQP_10:
                return AmqpConnectionActor.props(amqpConnection, commandProcessor, JMS_CONNECTION_FACTORY);
            default:
                throw new IllegalArgumentException(
                        "ConnectionType <" + amqpConnection.getConnectionType() + "> is not supported.");
        }
    }
}
