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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import akka.NotUsed;
import akka.kafka.ProducerMessage;
import akka.stream.javadsl.Flow;

/**
 * Creates Kafka sinks.
 */
interface KafkaConnectionFactory {

    /**
     * Identifier of the connection.
     *
     * @return the ID.
     */
    String connectionId();

    /**
     * Create an Akka stream flow of Kafka messages.
     *
     * @param <T> type of the pass through object.
     * @return Akka stream flow that publishes Kafka messages to the broker.
     */
    <T> Flow<ProducerMessage.Envelope<String, String, T>, ProducerMessage.Results<String, String, T>, NotUsed> newFlow();

}
