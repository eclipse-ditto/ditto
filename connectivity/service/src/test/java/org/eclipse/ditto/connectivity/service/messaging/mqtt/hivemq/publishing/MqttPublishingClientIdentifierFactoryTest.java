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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;

/**
 * Unit test for {@link MqttPublishingClientIdentifierFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttPublishingClientIdentifierFactoryTest {

    private static final UUID ACTOR_UUID = UUID.randomUUID();

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

    @Mock
    private Connection connection;

    @Mock
    private MqttConfig mqttConfig;

    @Before
    public void before() {
        Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);
        Mockito.when(connection.getClientCount()).thenReturn(1);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MqttPublishingClientIdentifierFactory.class, areImmutable());
    }

    @Test
    public void newInstanceWithNullMqttSpecificConfigThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishingClientIdentifierFactory.newInstance(null, connection, ACTOR_UUID))
                .withMessage("The mqttSpecificConfig must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullMqttConnectionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishingClientIdentifierFactory.newInstance(
                        MqttSpecificConfig.fromConnection(connection, mqttConfig),
                        null,
                        ACTOR_UUID
                ))
                .withMessage("The mqttConnection must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullActorUuidThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishingClientIdentifierFactory.newInstance(
                        MqttSpecificConfig.fromConnection(connection, mqttConfig),
                        connection,
                        null
                ))
                .withMessage("The actorUuid must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesPublisherIdAndClientCountIsOne() {
        final var publisherId = "myPublisher";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("publisherId", publisherId));
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(publisherId));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesPublisherIdAndClientCountIsTwo() {
        final var publisherId = "myPublisher";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("publisherId", publisherId));
        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}_{1}", publisherId, ACTOR_UUID)));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesMqttClientIdAndClientCountIsOne() {
        final var clientId = "myClient";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("clientId", clientId));
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(clientId + "p"));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesMqttClientIdAndClientCountIsTwo() {
        final var clientId = "myClient";
        Mockito.when(connection.getSpecificConfig()).thenReturn(Map.of("clientId", clientId));
        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}p_{1}", clientId, ACTOR_UUID)));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesNeitherPublisherIdNorMqttClientIdAndClientCountIsOne() {
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier()).isEqualTo(MqttClientIdentifier.of(connection.getId() + "p"));
    }

    @Test
    public void getMqttClientIdentifierReturnsExpectedIfMqttSpecificConfigProvidesNeitherPublisherIdNorMqttClientIdAndClientCountIsTwo() {
        Mockito.when(connection.getClientCount()).thenReturn(2);
        final var underTest = MqttPublishingClientIdentifierFactory.newInstance(
                MqttSpecificConfig.fromConnection(connection, mqttConfig),
                connection,
                ACTOR_UUID
        );

        assertThat(underTest.getMqttClientIdentifier())
                .isEqualTo(MqttClientIdentifier.of(MessageFormat.format("{0}p_{1}", connection.getId(), ACTOR_UUID)));
    }

}