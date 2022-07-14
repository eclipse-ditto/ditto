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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.messaging.internal.AbstractWithOrigin;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;

import akka.actor.ActorRef;

/**
 * Message type to indicate that an MQTT client connected to a broker.
 */
final class MqttClientConnected extends AbstractWithOrigin implements ClientConnected {

    private MqttClientConnected(@Nullable final ActorRef origin) {
        super(origin);
    }

    /**
     * Returns an instance of {@code MqttClientConnected} with the specified {@code ActorRef} argument.
     *
     * @param origin the origin that cause the client to connect or {@code null} if unknown.
     * @return the instance.
     */
    static MqttClientConnected of(@Nullable final ActorRef origin) {
        return new MqttClientConnected(origin);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + super.toString() + "]";
    }

}
