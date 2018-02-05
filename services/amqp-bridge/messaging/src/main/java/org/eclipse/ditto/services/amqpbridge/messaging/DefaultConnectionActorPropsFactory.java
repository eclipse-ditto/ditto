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
import org.eclipse.ditto.services.amqpbridge.messaging.amqp.AmqpConnectionActor;
import org.eclipse.ditto.services.amqpbridge.messaging.rabbitmq.RabbitMQConnectionActor;

import akka.actor.ActorRef;
import akka.actor.Props;

public class DefaultConnectionActorPropsFactory implements ConnectionActorPropsFactory {

    private final ActorRef pubSubMediator;
    private final String pubSubTargetActorPath;

    private DefaultConnectionActorPropsFactory(final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        this.pubSubMediator = pubSubMediator;
        this.pubSubTargetActorPath = pubSubTargetActorPath;
    }

    public static ConnectionActorPropsFactory getInstance(final ActorRef pubSubMediator,
            final String pubSubTargetActorPath) {
        return new DefaultConnectionActorPropsFactory(pubSubMediator, pubSubTargetActorPath);
    }

    @Override
    public Props getActorPropsForType(final String connectionId) {
        final ConnectionType connectionType = extractConnectionTypeAndId(connectionId);
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQConnectionActor.props(connectionId, pubSubMediator, pubSubTargetActorPath);
            case AMQP_10:
                return AmqpConnectionActor.props(connectionId, pubSubMediator, pubSubTargetActorPath);
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }

    private ConnectionType extractConnectionTypeAndId(final String actorName) {
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
