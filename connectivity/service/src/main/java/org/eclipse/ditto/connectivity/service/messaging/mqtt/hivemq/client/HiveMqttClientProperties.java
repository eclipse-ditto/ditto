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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nullable;

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
    private final UUID actorUuid;
    private final GenericMqttClientConnectedListener mqttClientConnectedListener;
    private final GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;
    private final boolean disableLastWillMessage;

    private HiveMqttClientProperties(final Builder builder) {
        mqttConnection = builder.mqttConnection;
        connectivityConfig = builder.connectivityConfig;
        mqttConfig = connectivityConfig.getConnectionConfig().getMqttConfig();
        mqttSpecificConfig = builder.mqttSpecificConfig;
        sshTunnelStateSupplier = builder.sshTunnelStateSupplier;
        connectionLogger = builder.connectionLogger;
        actorUuid = builder.actorUuid;
        mqttClientConnectedListener = builder.mqttClientConnectedListener;
        mqttClientDisconnectedListener = builder.mqttClientDisconnectedListener;
        disableLastWillMessage = builder.disableLastWillMessage;
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

    public MqttConfig getMqttConfig() {
        return mqttConfig;
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

    public ConnectivityConfig getConnectivityConfig() {
        return connectivityConfig;
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

    /**
     * Indicates whether the Last Will Message should be disabled while connection the client to the broker despite it
     * was required via config.
     *
     * @return {@code true} if no Last Will Message should be set during client connection.
     */
    public boolean isDisableLastWillMessage() {
        return disableLastWillMessage;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (HiveMqttClientProperties) o;
        return disableLastWillMessage == that.disableLastWillMessage &&
                Objects.equals(mqttConnection, that.mqttConnection) &&
                Objects.equals(connectivityConfig, that.connectivityConfig) &&
                Objects.equals(mqttConfig, that.mqttConfig) &&
                Objects.equals(mqttSpecificConfig, that.mqttSpecificConfig) &&
                Objects.equals(sshTunnelStateSupplier, that.sshTunnelStateSupplier) &&
                Objects.equals(connectionLogger, that.connectionLogger) &&
                Objects.equals(actorUuid, that.actorUuid) &&
                Objects.equals(mqttClientConnectedListener, that.mqttClientConnectedListener) &&
                Objects.equals(mqttClientDisconnectedListener, that.mqttClientDisconnectedListener);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mqttConnection,
                connectivityConfig,
                mqttConfig,
                mqttSpecificConfig,
                sshTunnelStateSupplier,
                connectionLogger,
                actorUuid,
                mqttClientConnectedListener,
                mqttClientDisconnectedListener,
                disableLastWillMessage);
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
        private UUID actorUuid;
        private GenericMqttClientConnectedListener mqttClientConnectedListener;
        private GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;
        private boolean disableLastWillMessage;

        private Builder() {
            mqttConnection = null;
            connectivityConfig = null;
            mqttSpecificConfig = null;
            sshTunnelStateSupplier = null;
            connectionLogger = null;
            actorUuid = null;
            mqttClientConnectedListener = (context, clientRole) -> {/* Do nothing.*/};
            mqttClientDisconnectedListener = (context, clientRole) -> {/* Do nothing.*/};
            disableLastWillMessage = false;
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
        public HiveMqttClientPropertiesStepBuilder.BuildableStep disableLastWillMessage() {
            disableLastWillMessage = true;
            return this;
        }

        @Override
        public HiveMqttClientProperties build() {
            return new HiveMqttClientProperties(this);
        }

    }

}
