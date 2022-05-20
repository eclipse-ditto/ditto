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
 * Factory for creating instances of {@link GenericMqttClient}.
 */
public interface GenericMqttClientFactory {

    /**
     * Returns an instance of {@link GenericMqttClient} for use in production.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @return the new {@code GenericMqttClient}.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    GenericMqttClient getProductiveGenericMqttClient(HiveMqttClientProperties hiveMqttClientProperties);

    /**
     * Returns an instance of {@link GenericMqttClient} for testing a connection.
     *
     * @param hiveMqttClientProperties properties which are required for creating a HiveMQ MQTT client.
     * @return the new {@code GenericMqttClient}.
     * @throws NullPointerException if {@code hiveMqttClientProperties} is {@code null}.
     */
    GenericMqttClient getGenericMqttClientForConnectionTesting(HiveMqttClientProperties hiveMqttClientProperties);

}
