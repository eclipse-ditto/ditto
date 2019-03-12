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

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;

import com.typesafe.config.Config;

import akka.kafka.ProducerMessage;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.Producer;
import akka.stream.javadsl.Flow;

/**
 * Creates Kafka sinks.
 */
final class DefaultKafkaConnectionFactory implements KafkaConnectionFactory {

    private final Connection connection;
    private final ProducerSettings<String, String> settings;
    private final org.apache.kafka.clients.producer.Producer<String, String> kafkaProducer;

    DefaultKafkaConnectionFactory(final Connection connection, final DittoHeaders dittoHeaders, final Config config) {
        this.connection = connection;
        settings = ProducerSettingsFactory.getInstance().createProducerSettings(connection, dittoHeaders, config);
        kafkaProducer = settings.createKafkaProducer();
    }

    @Override
    public String connectionId() {
        return connection.getId();
    }

    @Override
    public <T> Flow<ProducerMessage.Envelope<String, String, T>, ProducerMessage.Results<String, String, T>, akka.NotUsed> newFlow() {
        return Producer.flexiFlow(settings, kafkaProducer);
    }

}
