/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import java.util.concurrent.CompletionStage;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;

import com.typesafe.config.Config;

import akka.Done;
import akka.stream.javadsl.Sink;

/**
 * Creates Kafka sinks.
 */
public interface KafkaConnectionFactory {

    /**
     * Identifier of the connection.
     *
     * @return the ID.
     */
    String connectionId();

    /**
     * Create an Akka stream sink of Kafka messages.
     *
     * @return Akka stream sink that publishes Kafka messages to the broker.
     */
    Sink<ProducerRecord<String, String>, CompletionStage<Done>> newSink();

    /**
     * Create a default Kafka connection factory.
     *
     * @param connection The Kafka connection.
     * @param dittoHeaders Ditto headers.
     * @return an Kafka connection factory.
     */
    static KafkaConnectionFactory of(final Connection connection, final DittoHeaders dittoHeaders, final Config config) {
        return new DefaultKafkaConnectionFactory(connection, dittoHeaders, config);
    }
}
