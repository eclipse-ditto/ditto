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

/**
 * Identifies the role of a particular MQTT client.
 */
public enum ClientRole {

    /**
     * A client for consuming incoming MQTT Publish messages from the broker.
     */
    CONSUMER("consumer"),

    /**
     * A client for sending MQTT Publish messages to the broker.
     */
    PUBLISHER("publisher"),

    /**
     * A client for both, consuming incoming MQTT Publish messages from the broker and sending MQTT Publish messages to
     * the broker.
     */
    CONSUMER_PUBLISHER(CONSUMER + "+" + PUBLISHER);

    private final String name;

    ClientRole(final String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

}
