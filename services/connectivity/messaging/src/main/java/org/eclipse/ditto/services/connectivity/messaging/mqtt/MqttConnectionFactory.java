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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.Source;

import akka.Done;
import akka.stream.alpakka.mqtt.MqttMessage;
import akka.stream.javadsl.Sink;

/**
 * Create MQTT sources and sinks.
 */
public interface MqttConnectionFactory {

    /**
     * Identifier of the connection.
     *
     * @return the ID.
     */
    EntityId connectionId();

    /**
     * Create an Akka stream source of MQTT messages.
     *
     * @param mqttSource Connection source containing topics to subscribe to.
     * @param bufferSize maximum number of messages to keep for QoS 1 and 2.
     * @return Akka stream source that emits MQTT messages from the broker.
     */
    akka.stream.javadsl.Source<MqttMessage, CompletionStage<Done>> newSource(Source mqttSource, int bufferSize);

    /**
     * Create an Akka stream sink of MQTT messages.
     *
     * @return Akka stream sink that publishes MQTT messages to the broker.
     */
    Sink<MqttMessage, CompletionStage<Done>> newSink();

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
