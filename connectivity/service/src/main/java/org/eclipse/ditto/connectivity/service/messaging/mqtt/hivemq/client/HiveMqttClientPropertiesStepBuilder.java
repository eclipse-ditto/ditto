/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import java.util.UUID;
import java.util.function.Supplier;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;

/**
 * A mutable builder with a fluent API for step-wise constructing a {@code HiveMqttClientProperties}.
 * The design of this builder ensures that all mandatory properties have to be set by callers.
 */
public interface HiveMqttClientPropertiesStepBuilder {

    /**
     * Builder step for setting the MQTT {@link Connection}.
     */
    interface MqttConnectionStep {

        /**
         * Sets the specified MQTT connection to this builder.
         *
         * @param mqttConnection the MQTT connection to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code mqttConnection} is {@code null}.
         * @throws NoMqttConnectionException if the type of {@code mqttConnection} is neither
         * {@link ConnectionType#MQTT} nor {@link ConnectionType#MQTT_5}.
         */
        ConnectivityConfigStep withMqttConnection(Connection mqttConnection) throws NoMqttConnectionException;

    }

    /**
     * Builder step for setting the {@link ConnectivityConfig}.
     */
    interface ConnectivityConfigStep {

        /**
         * Sets the specified connectivity config to this builder.
         *
         * @param connectivityConfig the connectivity config to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code connectivityConfig} is {@code null}.
         */
        MqttSpecificConfigStep withConnectivityConfig(ConnectivityConfig connectivityConfig);

    }

    /**
     * Builder step for setting the {@link MqttSpecificConfig}.
     */
    interface MqttSpecificConfigStep {

        /**
         * Sets the MQTT specific config to this builder.
         *
         * @param mqttSpecificConfig the specific MQTT config to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code mqttSpecificConfig} is {@code null}.
         */
        SshTunnelStateSupplierStep withMqttSpecificConfig(MqttSpecificConfig mqttSpecificConfig);

    }

    /**
     * Builder step for setting the {@code Supplier} for the {@link SshTunnelState}.
     */
    interface SshTunnelStateSupplierStep {

        /**
         * Sets the specified supplier for the {@link SshTunnelState}.
         *
         * @param sshTunnelStateSupplier the supplier to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code sshTunnelStateSupplier} is {@code null}.
         */
        ConnectionLoggerStep withSshTunnelStateSupplier(Supplier<SshTunnelState> sshTunnelStateSupplier);

    }

    /**
     * Builder step for setting the {@link ConnectionLogger}.
     */
    interface ConnectionLoggerStep {

        /**
         * Sets the specified {@code ConnectionLogger}.
         *
         * @param connectionLogger the connection logger to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code connectionLogger} is {@code null}.
         */
        ActorUuidStep withConnectionLogger(ConnectionLogger connectionLogger);

    }

    /**
     * Builder step for setting the actor {@link UUID}.
     */
    interface ActorUuidStep {

        /**
         * Sets the specified actor UUID.
         *
         * @param actorUuid the UUID to be set.
         * @return the next builder step.
         * @throws NullPointerException if {@code actorUuid} is {@code null}.
         */
        BuildableStep withActorUuid(UUID actorUuid);

    }

    /**
     * Builder step for setting optional properties and eventually construct an instance of
     * {@code HiveMqttClientProperties}.
     */
    interface BuildableStep {

        /**
         * Sets the listener which is notified when the client is connected (a successful ConnAck message is received).
         *
         * @param listener the listener to set.
         * @return this builder instance.
         * @throws NullPointerException if {@code listener} is {@code null}.
         */
        BuildableStep withClientConnectedListener(GenericMqttClientConnectedListener listener);

        /**
         * Sets the listener which is notified when the client is disconnected (with or without a Disconnect message) or
         * the connection fails.
         *
         * @param listener the listener to set.
         * @return this builder instance.
         * @throws NullPointerException if {@code listener} is {@code null}.
         */
        BuildableStep withClientDisconnectedListener(GenericMqttClientDisconnectedListener listener);

        /**
         * Determines that no Last Will Message is set when connecting the client to the broker.
         * This overwrites the configuration and is intended for connection testing only.
         *
         * @return this builder instance.
         */
        BuildableStep disableLastWillMessage();

        /**
         * Creates a new instance of {@code HiveMqttClientProperties} with the properties of this builder.
         *
         * @return the new {@code HiveMqttClientProperties}.
         */
        HiveMqttClientProperties build();

    }

}
