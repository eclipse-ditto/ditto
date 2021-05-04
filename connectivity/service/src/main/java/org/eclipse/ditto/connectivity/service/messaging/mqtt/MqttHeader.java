/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Defines well-known MQTT properties that should be extracted from consumed mqtt messages and made available for
 * source header mappings. E.g. the mqtt topic on which the message was received is available in the header
 * {@code mqtt.topic}.
 */
public enum MqttHeader {

    MQTT_TOPIC("mqtt.topic"),
    MQTT_QOS("mqtt.qos"),
    MQTT_RETAIN("mqtt.retain");

    private final String name;

    /**
     * @param name the header name to be used in source header mappings
     */
    MqttHeader(final String name) {
        this.name = name;
    }

    /**
     * @return the header name
     */
    public String getName() {
        return name;
    }

    /**
     * @return list of default header names used for mqtt sources
     */
    public static List<String> getHeaderNames() {
        return Arrays.stream(values()).map(MqttHeader::getName).collect(Collectors.toList());
    }

}
