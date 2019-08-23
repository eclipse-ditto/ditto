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

/* Copyright (c) 2011-2018 Bosch Software Innovations GmbH, Germany. All rights reserved. */
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;

/**
 * Factory used to create {@link Mqtt3Client}s.
 */
public interface HiveMqtt3ClientFactory {


    /**
     * Creates a new {@link Mqtt3Client}.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @param connectedListener the connected listener passed to the created client
     * @param disconnectedListener the disconnected listener passed to the created client
     * @return the new {@link Mqtt3Client}
     */
    Mqtt3Client newClient(final Connection connection, final String identifier,
            @Nullable final MqttClientConnectedListener connectedListener,
            @Nullable final MqttClientDisconnectedListener disconnectedListener);


    /**
     * reates a new {@link Mqtt3Client}.
     *
     * @param connection the connection containing the configuration
     * @param identifier the identifier of the client
     * @return the new {@link Mqtt3Client}
     */
    default Mqtt3Client newClient(final Connection connection, final String identifier) {
        return newClient(connection, identifier, null, null);
    }

}
