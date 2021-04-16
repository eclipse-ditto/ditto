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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Defines well-known MQTT properties.
 */
public enum Mqtt3Header {

    MQTT_TOPIC("mqtt.topic"),
    MQTT_QOS("mqtt.qos"),
    MQTT_RETAIN("mqtt.retain");

    /**
     * Defines the default header mapping for mqtt3 sources.
     */
    public static final Map<String, String> DEFAULT_MQTT_HEADER_MAPPING = Map.of(
            MQTT_TOPIC.getName(), getHeaderPlaceholder(MQTT_TOPIC.getName()),
            MQTT_QOS.getName(), getHeaderPlaceholder(MQTT_QOS.getName()),
            MQTT_RETAIN.getName(), getHeaderPlaceholder(MQTT_RETAIN.getName())
    );

    private final String name;

    /**
     * @param name the actual header name as it is used in then (internal) message headers
     */
    Mqtt3Header(final String name) {
        this.name = name;
    }

    /**
     * @return the header name
     */
    public String getName() {
        return name;
    }

    private static String getHeaderPlaceholder(final String headerName) {
        return "{{ header:" + headerName + "}}";
    }

    /**
     * @return list of default header names used for mqtt sources
     */
    public static List<String> getHeaderNames() {
        return Arrays.stream(values()).map(Mqtt3Header::getName).collect(Collectors.toList());
    }

}
