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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

/**
 * Unit test for {@link MqttConnectionTypeValidator}.
 */
@RunWith(Enclosed.class)
public final class MqttConnectionTypeValidatorTest {

    public static final class GeneralFunctionalityTest {

        @Test
        public void assertImmutability() {
            assertInstancesOf(MqttConnectionTypeValidator.class, areImmutable());
        }

        @Test
        public void assertThatMqttConnectionTypeWithNullConnectionThrowsException() {
            Assertions.assertThatNullPointerException()
                    .isThrownBy(() -> MqttConnectionTypeValidator.assertThatMqttConnectionType(null))
                    .withMessage("The mqttConnection must not be null!")
                    .withNoCause();
        }

    }

    @RunWith(Parameterized.class)
    public static final class ParameterizedTest {

        private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();

        @Parameterized.Parameter
        public ConnectionType connectionType;

        @Parameterized.Parameters(name = "{0}")
        public static ConnectionType[] parameters() {
            return ConnectionType.values();
        }

        @Test
        public void assertThatMqttConnectionTypeWorksAsExpected() {
            final var connection = Mockito.mock(Connection.class);
            Mockito.when(connection.getConnectionType()).thenReturn(connectionType);
            Mockito.when(connection.getId()).thenReturn(CONNECTION_ID);

            if (isMqttType()) {
                assertThatCode(() -> MqttConnectionTypeValidator.assertThatMqttConnectionType(connection))
                        .doesNotThrowAnyException();
            } else {
                assertThatExceptionOfType(NoMqttConnectionException.class)
                        .isThrownBy(() -> MqttConnectionTypeValidator.assertThatMqttConnectionType(connection))
                        .withMessage("Expected type of connection <%s> to be one of %s but it was <%s>.",
                                CONNECTION_ID,
                                MqttConnectionTypeValidator.MQTT_CONNECTION_TYPES,
                                connectionType)
                        .withNoCause();
            }
        }

        private boolean isMqttType() {
            return MqttConnectionTypeValidator.MQTT_CONNECTION_TYPES.contains(connectionType);
        }

    }

}