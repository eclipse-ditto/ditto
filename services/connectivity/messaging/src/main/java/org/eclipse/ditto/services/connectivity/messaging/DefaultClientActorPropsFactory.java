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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.services.connectivity.mapping.MappingConfig;
import org.eclipse.ditto.services.connectivity.messaging.amqp.AmqpClientActor;
import org.eclipse.ditto.services.connectivity.messaging.config.ClientConfig;
import org.eclipse.ditto.services.connectivity.messaging.config.ConnectionConfig;
import org.eclipse.ditto.services.connectivity.messaging.kafka.DefaultKafkaPublisherActorFactory;
import org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaClientActor;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttClientActor;
import org.eclipse.ditto.services.connectivity.messaging.rabbitmq.RabbitMQClientActor;
import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * The default implementation of {@link ClientActorPropsFactory}.
 */
@Immutable
public final class DefaultClientActorPropsFactory implements ClientActorPropsFactory {

    private final ClientConfig clientConfig;
    private final MappingConfig mappingConfig;
    private final ProtocolConfig protocolConfig;
    private final ConnectionConfig connectionConfig;

    private DefaultClientActorPropsFactory(final ClientConfig clientConfig,
            final MappingConfig mappingConfig,
            final ProtocolConfig protocolConfig,
            final ConnectionConfig connectionConfig) {

        this.clientConfig = checkNotNull(clientConfig, "ClientConfig");
        this.mappingConfig = checkNotNull(mappingConfig, "MappingConfig");
        this.protocolConfig = checkNotNull(protocolConfig, "ProtocolConfig");
        this.connectionConfig = checkNotNull(connectionConfig, "ConnectionConfig");
    }

    /**
     * Returns an instance of {@code DefaultClientActorPropsFactory}.
     *
     * @param clientConfig the client config.
     * @param mappingConfig the mapping config.
     * @param protocolConfig the configuration settings for protocol mapping.
     * @param connectionConfig the connection config.
     * @return the factory instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static DefaultClientActorPropsFactory getInstance(final ClientConfig clientConfig,
            final MappingConfig mappingConfig,
            final ProtocolConfig protocolConfig,
            final ConnectionConfig connectionConfig) {

        return new DefaultClientActorPropsFactory(clientConfig, mappingConfig, protocolConfig, connectionConfig);
    }

    @Override
    public Props getActorPropsForType(final Connection connection, final ActorRef conciergeForwarder) {
        final ConnectionType connectionType = connection.getConnectionType();
        switch (connectionType) {
            case AMQP_091:
                return RabbitMQClientActor.props(connection, clientConfig, mappingConfig, protocolConfig,
                        conciergeForwarder);
            case AMQP_10:
                return AmqpClientActor.props(connection, clientConfig, mappingConfig, protocolConfig,
                        conciergeForwarder);
            case MQTT:
                return MqttClientActor.props(connection, clientConfig, mappingConfig, protocolConfig,
                        connectionConfig.getMqttConfig(), conciergeForwarder);
            case KAFKA:
                return KafkaClientActor.props(connection, clientConfig, mappingConfig, protocolConfig,
                        connectionConfig.getKafkaConfig(), conciergeForwarder,
                        DefaultKafkaPublisherActorFactory.getInstance());
            default:
                throw new IllegalArgumentException("ConnectionType <" + connectionType + "> is not supported.");
        }
    }

}
