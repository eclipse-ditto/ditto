/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.connectivity.Connection;

/**
 * Class providing access to MQTT specific configuration.
 */
@Immutable
public final class MqttSpecificConfig {

    private static final String CLIENT_ID = "clientId";

    private final Map<String, String> specificConfig;

    private MqttSpecificConfig(final Map<String, String> specificConfig) {
        this.specificConfig = Collections.unmodifiableMap(new HashMap<>(specificConfig));
    }

    /**
     * Creates a new instance of MqttSpecificConfig based on the {@code specificConfig} of the passed
     * {@code connection}.
     *
     * @param connection the Connection to extract the {@code specificConfig} map from.
     * @return the new MqttSpecificConfig instance
     */
    public static MqttSpecificConfig fromConnection(final Connection connection) {
        return new MqttSpecificConfig(connection.getSpecificConfig());
    }

    /**
     * @return the optional clientId which should be used by the MQTT client when connecting to the MQTT broker.
     */
    public Optional<String> getMqttClientId() {
        return Optional.ofNullable(specificConfig.get(CLIENT_ID));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MqttSpecificConfig that = (MqttSpecificConfig) o;
        return Objects.equals(specificConfig, that.specificConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specificConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "specificConfig=" + specificConfig +
                "]";
    }
}
