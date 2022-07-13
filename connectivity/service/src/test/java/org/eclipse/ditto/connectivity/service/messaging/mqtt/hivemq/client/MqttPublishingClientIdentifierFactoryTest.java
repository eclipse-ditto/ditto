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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectionConfig;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;

/**
 * Unit test for {@link MqttClientIdentifierFactory} for publishing client.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttPublishingClientIdentifierFactoryTest {

    private static final UUID ACTOR_UUID = UUID.randomUUID();

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Mock private Connection connection;
    @Mock private MqttConfig mqttConfig;
    @Mock private ConnectivityConfig connectivityConfig;
    @Mock private ConnectionLogger connectionLogger;

    @Before
    public void before() {
        Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);
        Mockito.when(connection.getConnectionType()).thenReturn(ConnectionType.MQTT_5);
        Mockito.when(connection.getClientCount()).thenReturn(1);

        final var connectionConfig = Mockito.mock(ConnectionConfig.class);
        Mockito.when(connectionConfig.getMqttConfig()).thenReturn(mqttConfig);
        Mockito.when(connectivityConfig.getConnectionConfig()).thenReturn(connectionConfig);
    }

    @Test
    public void ofWithNullHiveMqttClientPropertiesThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttClientIdentifierFactory.forPublishingClient(null))
                .withMessage("The hiveMqttClientProperties must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesPublisherIdAndClientCountIsOne()
            throws NoMqttConnectionException {

        final var publisherId = "myPublisher";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("publisherId", publisherId));
        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(publisherId));
    }

    private HiveMqttClientProperties getHiveMqttClientProperties() throws NoMqttConnectionException {
        return HiveMqttClientProperties.builder()
                .withMqttConnection(connection)
                .withConnectivityConfig(connectivityConfig)
                .withMqttSpecificConfig(MqttSpecificConfig.fromConnection(connection, mqttConfig))
                .withSshTunnelStateSupplier(() -> null)
                .withConnectionLogger(connectionLogger)
                .withActorUuid(ACTOR_UUID)
                .build();
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesPublisherIdAndClientCountIsTwo()
            throws NoMqttConnectionException {

        final var publisherId = "myPublisher";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("publisherId", publisherId));
        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}_{1}", publisherId, ACTOR_UUID)));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesMqttClientIdAndClientCountIsOne()
            throws NoMqttConnectionException {

        final var clientId = "myClient";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("clientId", clientId));
        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(clientId + "p"));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesMqttClientIdAndClientCountIsTwo()
            throws NoMqttConnectionException {

        final var clientId = "myClient";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("clientId", clientId));
        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}p_{1}", clientId, ACTOR_UUID)));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesNeitherPublisherIdNorMqttClientIdAndClientCountIsOne()
            throws NoMqttConnectionException {

        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(connection.getId() + "p"));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesNeitherPublisherIdNorMqttClientIdAndClientCountIsTwo()
            throws NoMqttConnectionException {

        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttClientIdentifierFactory.forPublishingClient(getHiveMqttClientProperties());

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}p_{1}", connection.getId(), ACTOR_UUID)));
    }

}