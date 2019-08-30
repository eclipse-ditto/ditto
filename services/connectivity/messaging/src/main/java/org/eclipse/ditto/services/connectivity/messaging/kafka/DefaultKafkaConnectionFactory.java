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

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.config.KafkaConfig;

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

    private DefaultKafkaConnectionFactory(final Connection connection,
            final ProducerSettings<String, String> producerSettings) {

        this.connection = connection;
        settings = producerSettings;
    }

    /**
     * Returns an instance of the default Kafka connection factory.
     *
     * @param connection the Kafka connection.
     * @param kafkaConfig the Kafka configuration settings.
     * @return an Kafka connection factory.
     */
    static DefaultKafkaConnectionFactory getInstance(final Connection connection, final KafkaConfig kafkaConfig) {
        final ProducerSettingsFactory settingsFactory = ProducerSettingsFactory.getInstance(connection, kafkaConfig);

        return new DefaultKafkaConnectionFactory(connection, settingsFactory.getProducerSettings());
    }

    @Override
    public EntityId connectionId() {
        return connection.getId();
    }

    @Override
    public <T> Flow<ProducerMessage.Envelope<String, String, T>, ProducerMessage.Results<String, String, T>, akka.NotUsed> newFlow() {
        return Producer.flexiFlow(settings);
    }

}
