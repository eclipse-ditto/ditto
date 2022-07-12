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

import java.text.MessageFormat;
import java.util.UUID;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.datatypes.MqttClientIdentifier;

/**
 * Base implementation of a factory for creating an MQTT client identifier.
 */
@Immutable
abstract class MqttClientIdentifierFactory {

    private final MqttSpecificConfig mqttSpecificConfig;
    private final ConnectionId connectionId;
    private final int clientCount;
    private final UUID actorUuid;

    private MqttClientIdentifierFactory(final HiveMqttClientProperties hiveMqttClientProperties) {
        checkNotNull(hiveMqttClientProperties, "hiveMqttClientProperties");
        mqttSpecificConfig = hiveMqttClientProperties.getMqttSpecificConfig();
        final var mqttConnection = hiveMqttClientProperties.getMqttConnection();
        connectionId = mqttConnection.getId();
        clientCount = mqttConnection.getClientCount();
        actorUuid = hiveMqttClientProperties.getActorUuid();
    }

    /**
     * Returns a {@code MqttClientIdentifierFactory} to be used for a {@link GenericMqttSubscribingClient}.
     *
     * @param hiveMqttClientProperties provides the properties which are required for creating the appropriate
     * client ID.
     * @return the ID factory for a generic MQTT subscribing client.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    static MqttClientIdentifierFactory forSubscribingClient(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return new MqttSubscribingClientIdentifierFactory(hiveMqttClientProperties);
    }

    /**
     * Returns a {@code MqttClientIdentifierFactory} to be used for a {@link GenericMqttPublishingClient}.
     *
     * @param hiveMqttClientProperties provides the properties which are required for creating the appropriate
     * client ID.
     * @return the ID factory for a generic MQTT publishing client.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    static MqttClientIdentifierFactory forPublishingClient(
            final HiveMqttClientProperties hiveMqttClientProperties
    ) {
        return new MqttPublishingClientIdentifierFactory(hiveMqttClientProperties);
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

    /**
     * Factory for the {@link MqttClientIdentifier} to be used for
     * a {@link GenericMqttSubscribingClient}.
     */
    @Immutable
    private static final class MqttSubscribingClientIdentifierFactory extends MqttClientIdentifierFactory {

        private MqttSubscribingClientIdentifierFactory(final HiveMqttClientProperties hiveMqttClientProperties) {
            super(hiveMqttClientProperties);
        }

        @Override
        protected String getBaseClientIdString(final MqttSpecificConfig mqttSpecificConfig,
                final ConnectionId connectionId) {

            return mqttSpecificConfig.getMqttClientId().orElseGet(() -> String.valueOf(connectionId));
        }

    }

    /**
     * Factory for the {@link MqttClientIdentifier} to be used for
     * a {@link GenericMqttPublishingClient}.
     */
    @Immutable
    private static final class MqttPublishingClientIdentifierFactory extends MqttClientIdentifierFactory {

        private MqttPublishingClientIdentifierFactory(final HiveMqttClientProperties hiveMqttClientProperties) {
            super(hiveMqttClientProperties);
        }

        @Override
        protected String getBaseClientIdString(final MqttSpecificConfig mqttSpecificConfig,
                final ConnectionId connectionId) {

            return mqttSpecificConfig.getMqttPublisherId()
                    .or(() -> mqttSpecificConfig.getMqttClientId().map(mqttClientId -> mqttClientId + "p"))
                    .orElseGet(() -> connectionId + "p");
        }

    }

}
