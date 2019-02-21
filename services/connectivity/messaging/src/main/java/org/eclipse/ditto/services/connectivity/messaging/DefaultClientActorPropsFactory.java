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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    private final ClientConfig clientConfig;
    private final MappingConfig mappingConfig;
    private final ConnectionConfig.MqttConfig mqttConfig;

    private DefaultClientActorPropsFactory(final ClientConfig clientConfig, final MappingConfig mappingConfig,
            final ConnectionConfig.MqttConfig mqttConfig) {

        this.clientConfig = checkNotNull(clientConfig, "ClientConfig");
        this.mappingConfig = checkNotNull(mappingConfig, "MappingConfig");
        this.mqttConfig = checkNotNull(mqttConfig, "MqttConfig");
    }

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}.
     *
     * @param clientConfig the client config.
     * @param mappingConfig the mapping config.
     * @param connectionConfig the connection config.
     * @return the factory instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultClientActorPropsFactory getInstance(final ClientConfig clientConfig,
            final MappingConfig mappingConfig, final ConnectionConfig connectionConfig) {

        return new DefaultClientActorPropsFactory(clientConfig, mappingConfig, connectionConfig.getMqttConfig());
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef conciergeForwarder) {
        final ConnectionType connectionType = connection.getConnectionType();
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQClientActor.props(connection, clientConfig, mappingConfig, conciergeForwarder);
            case AMQP_10:
                return AmqpClientActor.props(connection, clientConfig, mappingConfig, conciergeForwarder);
            case MQTT:
                return MqttClientActor.props(connection, clientConfig, mappingConfig, mqttConfig, conciergeForwarder);
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }

}
