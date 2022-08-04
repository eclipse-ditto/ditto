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

import java.text.MessageFormat;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;

/**
 * This utility class can be used to assert that the type of a particular {@link Connection} indicates a MQTT protocol.
 * If the connection has an inappropriate type a {@link NoMqttConnectionException} is thrown.
 */
@Immutable
final class MqttConnectionTypeValidator {

    static final Set<ConnectionType> MQTT_CONNECTION_TYPES =
            Collections.unmodifiableSet(EnumSet.of(ConnectionType.MQTT, ConnectionType.MQTT_5));

    private MqttConnectionTypeValidator() {
        throw new AssertionError();
    }

    /**
     * Asserts that the type of the specified {@code Connection} argument is an MQTT protocol.
     *
     * @param mqttConnection the connection of which the type is supposed to be an MQTT protocol.
     * @throws NoMqttConnectionException if the type of {@code mqttConnection} is neither {@link ConnectionType#MQTT}
     * nor {@link ConnectionType#MQTT_5}.
     */
    static void assertThatMqttConnectionType(final Connection mqttConnection) throws NoMqttConnectionException {
        ConditionChecker.checkNotNull(mqttConnection, "mqttConnection");
        final var connectionType = mqttConnection.getConnectionType();
        if (!MQTT_CONNECTION_TYPES.contains(connectionType)) {
            throw new NoMqttConnectionException(
                    MessageFormat.format("Expected type of connection <{0}> to be one of {1} but it was <{2}>.",
                            mqttConnection.getId(),
                            MQTT_CONNECTION_TYPES,
                            connectionType)
            );
        }
    }

}
