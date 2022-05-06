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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import java.util.UUID;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients.MqttClientIdentifierFactory;

/**
 * Factory for the {@link com.hivemq.client.mqtt.datatypes.MqttClientIdentifier} to be used for
 * a {@link GenericMqttSubscribingClient}.
 */
@Immutable
final class MqttSubscribingClientIdentifierFactory extends MqttClientIdentifierFactory {

    private MqttSubscribingClientIdentifierFactory(final MqttSpecificConfig mqttSpecificConfig,
            final Connection connection,
            final UUID actorUuid) {

        super(mqttSpecificConfig, connection, actorUuid);
    }

    /**
     * Returns a new instance of {@code MqttSubscribingClientIdentifierFactory} for the specified arguments.
     *
     * @param mqttSpecificConfig provides specific MQTT configuration properties that might be required for creating an
     * MQTT client ID.
     * @param mqttConnection MQTT connection that provides some properties which are required for creating an MQTT
     * client ID.
     * @param actorUuid UUID for making the MQTT client ID unique if necessary.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static MqttSubscribingClientIdentifierFactory newInstance(final MqttSpecificConfig mqttSpecificConfig,
            final Connection mqttConnection,
            final UUID actorUuid) {

        return new MqttSubscribingClientIdentifierFactory(mqttSpecificConfig, mqttConnection, actorUuid);
    }

    @Override
    protected String getBaseClientIdString(final MqttSpecificConfig mqttSpecificConfig,
            final ConnectionId connectionId) {

        return mqttSpecificConfig.getMqttClientId().orElseGet(() -> String.valueOf(connectionId));
    }

}
