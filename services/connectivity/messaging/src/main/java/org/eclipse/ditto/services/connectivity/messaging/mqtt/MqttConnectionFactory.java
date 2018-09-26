/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * SPDX-License-Identifier: EPL-2.0
 *
 */
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.MqttSource;

import akka.Done;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.javadsl.Source;

/**
 * Create MQTT sources and sinks.
 */
public interface MqttConnectionFactory {

    /**
     * Identifier of the connection.
     *
     * @return the ID.
     */
    String connectionId();

    /**
     * Create an Akka stream source of MQTT messages.
     *
     * @param mqttSource Connection source containing topics to subscribe to.
     * @param bufferSize maximum number of messages to keep for QoS 1 and 2.
     * @return Akka stream source that emits MQTT messages from the broker.
     */
    Source<MqttMessage, CompletionStage<Done>> newSource(final MqttSource mqttSource, final int bufferSize);

    /**
     * Create an Akka stream sink of MQTT messages.
     *
     * @return Akka stream sink that publishes MQTT messages to the broker.
     */
    akka.stream.javadsl.Sink<MqttMessage, CompletionStage<Done>> newSink();

    /**
     * Create a default MQTT connection factory.
     *
     * @param connection The MQTT connection.
     * @param dittoHeaders Ditto headers.
     * @return an MQTT connection factory.
     */
    static MqttConnectionFactory of(final Connection connection, final DittoHeaders dittoHeaders) {
        return new DefaultMqttConnectionFactory(connection, dittoHeaders);
    }
}
