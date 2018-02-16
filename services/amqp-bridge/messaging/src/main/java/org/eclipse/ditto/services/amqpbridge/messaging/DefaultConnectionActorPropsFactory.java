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

import java.util.Arrays;

import org.eclipse.ditto.model.amqpbridge.ConnectionType;
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ConnectionActorPropsFactory}.
 */
public class DefaultConnectionActorPropsFactory implements ConnectionActorPropsFactory {

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
    public Props getActorPropsForType(final ActorRef connectionActor, final String connectionId) {
        final ConnectionType connectionType = extractConnectionTypeFromId(connectionId);
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQClientActor.props(connectionId, connectionActor);
            case AMQP_10:
                return AmqpClientActor.props(connectionId, connectionActor);
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }

    private ConnectionType extractConnectionTypeFromId(final String actorName) {
        int idx = actorName.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException("Missing connection type prefix in " + actorName);
        }
        final String type = actorName.substring(0, idx);
        return ConnectionType.forName(type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "<" + type + "> is not one of the valid ConnectionTypes " +
                                Arrays.toString(ConnectionType.values()) + "."));
    }
}
