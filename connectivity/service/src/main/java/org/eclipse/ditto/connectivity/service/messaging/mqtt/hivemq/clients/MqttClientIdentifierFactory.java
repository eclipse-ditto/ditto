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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.UUID;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;

/**
 * Base implementation of a factory for creating an MQTT client identifier.
 */
@Immutable
public abstract class MqttClientIdentifierFactory {

    private final MqttSpecificConfig mqttSpecificConfig;
    private final ConnectionId connectionId;
    private final int clientCount;
    private final UUID actorUuid;

    /**
     * Constructs a {@code MqttClientIdentifierFactory}.
     *
     * @param mqttSpecificConfig provides specific MQTT configuration properties that might be required for creating an
     * MQTT client ID.
     * @param mqttConnection MQTT connection that provides some properties which are required for creating an MQTT
     * client ID.
     * @param actorUuid UUID for making the MQTT client ID unique if necessary.
     * @throws NullPointerException if any argument is {@code null}.
     */
    protected MqttClientIdentifierFactory(final MqttSpecificConfig mqttSpecificConfig,
            final Connection mqttConnection,
            final UUID actorUuid) {

        this.mqttSpecificConfig = checkNotNull(mqttSpecificConfig, "mqttSpecificConfig");
        checkNotNull(mqttConnection, "mqttConnection");
        connectionId = mqttConnection.getId();
        clientCount = mqttConnection.getClientCount();
        this.actorUuid = checkNotNull(actorUuid, "actorUuid");
    }

    /**
     * Creates an MQTT client ID.
     *
     * @return the MQTT client identifier.
     * @throws IllegalArgumentException if the designated client ID string is not a valid MQTT client identifier.
     */
    public MqttClientIdentifier getMqttClientIdentifier() {
        final String clientIdString;
        final var baseClientIdString = getBaseClientIdString(mqttSpecificConfig, connectionId);
        if (baseClientIdString.isEmpty()) {
            clientIdString = baseClientIdString;
        } else if (1 >= clientCount) {
            clientIdString = baseClientIdString;
        } else {
            clientIdString = MessageFormat.format("{0}_{1}", baseClientIdString, actorUuid);
        }
        return MqttClientIdentifier.of(clientIdString);
    }

    protected abstract String getBaseClientIdString(MqttSpecificConfig mqttSpecificConfig, ConnectionId connectionId);

}
