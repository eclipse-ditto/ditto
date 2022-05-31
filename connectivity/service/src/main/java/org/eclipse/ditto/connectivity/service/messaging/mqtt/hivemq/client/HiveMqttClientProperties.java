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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;

/**
 * Properties which are required for creating a HiveMQ MQTT client.
 */
public final class HiveMqttClientProperties {

    private final Connection mqttConnection;
    private final ConnectivityConfig connectivityConfig;
    private final MqttConfig mqttConfig;
    private final MqttSpecificConfig mqttSpecificConfig;
    private final Supplier<SshTunnelState> sshTunnelStateSupplier;
    private final ConnectionLogger connectionLogger;
    private final GenericMqttClientConnectedListener mqttClientConnectedListener;
    private final GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;
    private final UUID actorUuid;

    private HiveMqttClientProperties(final Builder builder) {
        mqttConnection = builder.mqttConnection;
        connectivityConfig = builder.connectivityConfig;
        mqttConfig = connectivityConfig.getConnectionConfig().getMqttConfig();
        mqttSpecificConfig = builder.mqttSpecificConfig;
        sshTunnelStateSupplier = builder.sshTunnelStateSupplier;
        connectionLogger = builder.connectionLogger;
        mqttClientConnectedListener = builder.mqttClientConnectedListener;
        mqttClientDisconnectedListener = builder.mqttClientDisconnectedListener;
        actorUuid = builder.actorUuid;
    }

    /**
     * Returns a new builder with a fluent API for constructing a {@code HiveMqttClientProperties} object.
     *
     * @return the new builder instance.
     * {@link ConnectionType#MQTT_5}.
     */
    public static HiveMqttClientPropertiesStepBuilder.MqttConnectionStep builder() {
        return new Builder();
    }

    public MqttSpecificConfig getMqttSpecificConfig() {
        return mqttSpecificConfig;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    /**
     * Returns the optional SSH tunnel state.
     *
     * @return the SSH tunnel state or an empty Optional if the SSH tunnel state supplier returns {@code null}.
     */
    public Optional<SshTunnelState> getSshTunnelState() {
        return Optional.ofNullable(sshTunnelStateSupplier.get());
    }

    public int getEventLoopThreadNumber() {
        return mqttConfig.getEventLoopThreads();
    }

    /**
     * Returns the MQTT connection.
     * The type of the returned connection is guaranteed to be either {@link ConnectionType#MQTT} or
     * {@link ConnectionType#MQTT_5}.
     *
     * @return the MQTT connection.
     */
    public Connection getMqttConnection() {
        return mqttConnection;
    }

    public ConnectionId getConnectionId() {
        return mqttConnection.getId();
    }

    public ConnectionLogger getConnectionLogger() {
        return connectionLogger;
    }

    public GenericMqttClientConnectedListener getMqttClientConnectedListener() {
        return mqttClientConnectedListener;
    }

    public GenericMqttClientDisconnectedListener getMqttClientDisconnectedListener() {
        return mqttClientDisconnectedListener;
    }

    public ConnectivityConfig getConnectivityConfig() {
        return connectivityConfig;
    }

    private static final class Builder implements HiveMqttClientPropertiesStepBuilder.MqttConnectionStep,
            HiveMqttClientPropertiesStepBuilder.ConnectivityConfigStep,
            HiveMqttClientPropertiesStepBuilder.MqttSpecificConfigStep,
            HiveMqttClientPropertiesStepBuilder.SshTunnelStateSupplierStep,
            HiveMqttClientPropertiesStepBuilder.ConnectionLoggerStep,
            HiveMqttClientPropertiesStepBuilder.ActorUuidStep,
            HiveMqttClientPropertiesStepBuilder.BuildableStep {

        private Connection mqttConnection;
        private ConnectivityConfig connectivityConfig;
        private MqttSpecificConfig mqttSpecificConfig;
        private Supplier<SshTunnelState> sshTunnelStateSupplier;
        private ConnectionLogger connectionLogger;
        private GenericMqttClientConnectedListener mqttClientConnectedListener;
        private GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;
        private UUID actorUuid;

        private Builder() {
            mqttConnection = null;
            connectivityConfig = null;
            mqttSpecificConfig = null;
            sshTunnelStateSupplier = null;
            connectionLogger = null;
            mqttClientConnectedListener = (context, clientRole) -> {/* Do nothing.*/};
            mqttClientDisconnectedListener = (context, clientRole) -> {/* Do nothing.*/};
            actorUuid = null;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.ConnectivityConfigStep withMqttConnection(
                final Connection mqttConnection
        ) throws NoMqttConnectionException {
            MqttConnectionTypeValidator.assertThatMqttConnectionType(mqttConnection);
            this.mqttConnection = mqttConnection;
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.MqttSpecificConfigStep withConnectivityConfig(
                final ConnectivityConfig connectivityConfig
        ) {
            this.connectivityConfig = checkNotNull(connectivityConfig, "connectivityConfig");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.SshTunnelStateSupplierStep withMqttSpecificConfig(
                final MqttSpecificConfig mqttSpecificConfig
        ) {
            this.mqttSpecificConfig = checkNotNull(mqttSpecificConfig, "mqttSpecificConfig");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.ConnectionLoggerStep withSshTunnelStateSupplier(
                final Supplier<SshTunnelState> sshTunnelStateSupplier
        ) {
            this.sshTunnelStateSupplier = checkNotNull(sshTunnelStateSupplier, "sshTunnelStateSupplier");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.ActorUuidStep withConnectionLogger(
                final ConnectionLogger connectionLogger
        ) {
            this.connectionLogger = checkNotNull(connectionLogger, "connectionLogger");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.BuildableStep withActorUuid(final UUID actorUuid) {
            this.actorUuid = checkNotNull(actorUuid, "actorUuid");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.BuildableStep withClientConnectedListener(
                final GenericMqttClientConnectedListener mqttClientConnectedListener
        ) {
            this.mqttClientConnectedListener = checkNotNull(mqttClientConnectedListener, "mqttClientConnectedListener");
            return this;
        }

        @Override
        public HiveMqttClientPropertiesStepBuilder.BuildableStep withClientDisconnectedListener(
                final GenericMqttClientDisconnectedListener mqttClientDisconnectedListener
        ) {
            this.mqttClientDisconnectedListener =
                    checkNotNull(mqttClientDisconnectedListener, "mqttClientDisconnectedListener");
            return this;
        }

        @Override
        public HiveMqttClientProperties build() {
            return new HiveMqttClientProperties(this);
        }

    }

}
