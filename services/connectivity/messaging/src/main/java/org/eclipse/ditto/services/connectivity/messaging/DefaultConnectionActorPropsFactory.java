/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.Props;

/**
 * The default implementation of {@link ConnectionActorPropsFactory}.
 */
public final class DefaultConnectionActorPropsFactory implements ConnectionActorPropsFactory {

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
    public Props getActorPropsForType(final Connection connection) {
        final ConnectionType connectionType = connection.getConnectionType();
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQClientActor.props(connection);
            case AMQP_10:
                return AmqpClientActor.props(connection);
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }
}
