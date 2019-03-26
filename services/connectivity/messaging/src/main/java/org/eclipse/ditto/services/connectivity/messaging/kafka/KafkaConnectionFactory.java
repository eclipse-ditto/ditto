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

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.util.KafkaConfigReader;

import akka.NotUsed;
import akka.kafka.ProducerMessage;
import akka.stream.javadsl.Flow;

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
     * Create an Akka stream flow of Kafka messages.
     *
     * @return Akka stream flow that publishes Kafka messages to the broker.
     */
    <T> Flow<ProducerMessage.Envelope<String, String, T>, ProducerMessage.Results<String, String, T>, NotUsed> newFlow();

    /**
     * Create a default Kafka connection factory.
     *
     * @param connection the Kafka connection.
     * @param config the Kafka config reader.
     * @return an Kafka connection factory.
     */
    static KafkaConnectionFactory of(final Connection connection, final KafkaConfigReader config) {
        return new DefaultKafkaConnectionFactory(connection, config);
    }

}
