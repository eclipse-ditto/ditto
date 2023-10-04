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

import com.hivemq.client.mqtt.MqttVersion;

/**
 * Defines well-known MQTT properties that should be extracted from consumed mqtt messages and made available for
 * source header mappings. E.g. the mqtt topic on which the message was received is available in the header
 * {@code mqtt.topic}.
 */
public enum MqttHeader {

    MQTT_TOPIC("mqtt.topic", MqttVersion.MQTT_3_1_1),
    MQTT_QOS("mqtt.qos", MqttVersion.MQTT_3_1_1),
    MQTT_RETAIN("mqtt.retain", MqttVersion.MQTT_3_1_1),
    MQTT_MESSAGE_EXPIRY_INTERVAL("mqtt.message-expiry-interval", MqttVersion.MQTT_5_0);

    private final String name;

    /**
     * MQTT version where the header was introduced.
     */
    private final MqttVersion mqttVersion;

    /**
     * @param name the header name to be used in source header mappings
     * @param mqttVersion MQTT version where the header was introduced
     */
    MqttHeader(final String name, final MqttVersion mqttVersion) {
        this.name = name;
        this.mqttVersion = mqttVersion;
    }

    /**
     * @return the header name
     */
    public String getName() {
        return name;
    }

    /**
       @param mqttVersion MQTT version to get headers for
     * @return list of header names that are available in provided MQTT version
     */
    public static List<String> getHeaderNames(final MqttVersion mqttVersion) {
        return Arrays.stream(values())
                .filter(value -> value.isAvailableInMqttVersion(mqttVersion))
                .map(MqttHeader::getName)
                .toList();
    }

    /**
     * @return list of header names that are available in at least one MQTT version
     */
    public static List<String> getAllHeaderNames() {
        return Arrays.stream(values()).map(MqttHeader::getName).toList();
    }

    /**
     * @param mqttVersion MQTT version to check
     * @return true if the header is available in provided MQTT version otherwise false
     */
    private boolean isAvailableInMqttVersion(final MqttVersion mqttVersion) {
        return this.mqttVersion.compareTo(mqttVersion) <= 0;
    }

}
