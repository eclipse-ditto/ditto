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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link HiveMqttClientProperties}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class HiveMqttClientPropertiesTest {

    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Mock private Connection mqttConnection;
    @Mock private MqttConfig mqttConfig;
    @Mock private ConnectivityConfig connectivityConfig;
    @Mock private Supplier<SshTunnelState> sshTunnelStateSupplier;
    @Mock private ConnectionLogger connectionLogger;
    @Mock private GenericMqttClientConnectedListener mqttClientConnectedListener;
    @Mock private GenericMqttClientDisconnectedListener mqttClientDisconnectedListener;

    @Before
    public void before() {
        Mockito.when(mqttConnection.getConnectionType()).thenReturn(ConnectionType.MQTT_5);
    }

    @Test
    public void setNullMqttConnectionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder().withMqttConnection(null))
                .withMessage("The mqttConnection must not be null!")
                .withNoCause();
    }

    @Test
    public void setNonMqttConnectionThrowsException() {
        final var connectionTypeKafka = ConnectionType.KAFKA;
        Mockito.when(mqttConnection.getConnectionType()).thenReturn(connectionTypeKafka);
        final var connectionId = ConnectionId.generateRandom();
        Mockito.when(mqttConnection.getId()).thenReturn(connectionId);

        assertThatExceptionOfType(NoMqttConnectionException.class)
                .isThrownBy(() -> HiveMqttClientProperties.builder().withMqttConnection(mqttConnection))
                .withMessage("Expected type of connection <%s> to be one of %s but it was <%s>.",
                        connectionId,
                        List.of(ConnectionType.MQTT, ConnectionType.MQTT_5),
                        connectionTypeKafka)
                .withNoCause();
    }

    @Test
    public void setNullConnectivityConfigThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(null))
                .withMessage("The connectivityConfig must not be null!")
                .withNoCause();
    }

    @Test
    public void setNullSshTunnelStateSupplierThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(connectivityConfig)
                        .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                        .withSshTunnelStateSupplier(null))
                .withMessage("The sshTunnelStateSupplier must not be null!")
                .withNoCause();
    }

    @Test
    public void setNullConnectionLoggerThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(connectivityConfig)
                        .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                        .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                        .withConnectionLogger(null))
                .withMessage("The connectionLogger must not be null!")
                .withNoCause();
    }

    @Test
    public void setNullActorUuidThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(connectivityConfig)
                        .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                        .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                        .withConnectionLogger(connectionLogger)
                        .withActorUuid(null))
                .withMessage("The actorUuid must not be null!")
                .withNoCause();
    }

    @Test
    public void setNullClientConnectedListenerThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(connectivityConfig)
                        .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                        .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                        .withConnectionLogger(connectionLogger)
                        .withActorUuid(ACTOR_UUID)
                        .withClientConnectedListener(null))
                .withMessage("The mqttClientConnectedListener must not be null!")
                .withNoCause();
    }

    @Test
    public void setNullClientDisconnectedListenerThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> HiveMqttClientProperties.builder()
                        .withMqttConnection(mqttConnection)
                        .withConnectivityConfig(connectivityConfig)
                        .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                        .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                        .withConnectionLogger(connectionLogger)
                        .withActorUuid(ACTOR_UUID)
                        .withClientDisconnectedListener(null))
                .withMessage("The mqttClientDisconnectedListener must not be null!")
                .withNoCause();
    }

    @Test
    public void buildReturnsExpectedInstance() throws NoMqttConnectionException {
        final var mqttConfig = Mockito.mock(MqttConfig.class);
        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);
        final var sshTunnelState = SshTunnelState.disabled();
        Mockito.when(sshTunnelStateSupplier.get()).thenReturn(sshTunnelState);

        final var underTest = HiveMqttClientProperties.builder()
                .withMqttConnection(mqttConnection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig))
                .withSshTunnelStateSupplier(sshTunnelStateSupplier)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .withClientConnectedListener(mqttClientConnectedListener)
                .withClientDisconnectedListener(mqttClientDisconnectedListener)
                .build();

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(underTest.getMqttConnection()).as("mqtt connection").isEqualTo(mqttConnection);
            softly.assertThat(underTest.getMqttSpecificConfig())
                    .as("MQTT specific config")
                    .isEqualTo(MqttSpecificConfig.fromConnection(mqttConnection, mqttConfig));
            softly.assertThat(underTest.getConnectivityConfig())
                    .as("connectivity config")
                    .isEqualTo(connectivityConfig);
            softly.assertThat(underTest.getSshTunnelState()).as("SSH tunnel state").hasValue(sshTunnelState);
            softly.assertThat(underTest.getMqttConfig()).as("MQTT config").isEqualTo(mqttConfig);
            softly.assertThat(underTest.getConnectionLogger()).as("connection logger").isEqualTo(connectionLogger);
            softly.assertThat(underTest.getActorUuid()).as("actor UUID").isEqualTo(ACTOR_UUID);
            softly.assertThat(underTest.getMqttClientConnectedListener())
                    .as("MQTT client connected listener")
                    .isEqualTo(mqttClientConnectedListener);
            softly.assertThat(underTest.getMqttClientDisconnectedListener())
                    .as("MQTT client disconnected listener")
                    .isEqualTo(mqttClientDisconnectedListener);
        }
    }

}