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
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpConnectionBasedJmsConnectionFactory;
import org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ConnectionActorPropsFactory}.
 */
public class DefaultConnectionActorPropsFactory implements ConnectionActorPropsFactory {

    private static final AmqpConnectionBasedJmsConnectionFactory JMS_CONNECTION_FACTORY =
            AmqpConnectionBasedJmsConnectionFactory.getInstance();
    private static final DefaultConnectionActorPropsFactory INSTANCE = new DefaultConnectionActorPropsFactory();

    private DefaultConnectionActorPropsFactory() {
    }

    /**
     * @return factory instance
     */
    public static ConnectionActorPropsFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public Props getActorPropsForType(final AmqpConnection amqpConnection, final ActorRef commandProcessor) {
        switch (amqpConnection.getConnectionType()) {
            case AMQP_091:
                return RabbitMQClientActor.props(amqpConnection, commandProcessor);
            case AMQP_10:
                return AmqpClientActor.props(amqpConnection, commandProcessor, JMS_CONNECTION_FACTORY);
            default:
                throw new IllegalArgumentException(
                        "ConnectionType <" + amqpConnection.getConnectionType() + "> is not supported.");
        }
    }
}
