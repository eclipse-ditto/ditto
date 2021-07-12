/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;

import akka.actor.ActorSystem;

/**
 * Connection specification for Mqtt 5 protocol.
 */
@Immutable
public final class Mqtt5Validator extends AbstractMqttValidator {

    private Mqtt5Validator(final MqttConfig mqttConfig) {
        super(mqttConfig);
    }

    /**
     * Create a new {@code Mqtt5Validator}.
     *
     * @param mqttConfig used to create the fallback specific config
     * @return a new instance.
     */
    public static Mqtt5Validator newInstance(final MqttConfig mqttConfig) {
        return new Mqtt5Validator(mqttConfig);
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.MQTT_5;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "MQTT 5");
        validateAddresses(connection, dittoHeaders);
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
        validateSpecificConfig(connection, dittoHeaders);
    }

}
