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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;

/**
 * Creates Kafka sinks.
 */
final class DefaultKafkaConnectionFactory implements KafkaConnectionFactory {

    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    private final Connection connection;
    private final Map<String, Object> producerProperties;
    private final Properties consumerStreamProperties;

    private DefaultKafkaConnectionFactory(final Connection connection, final Map<String, Object> producerProperties,
            final Properties consumerStreamProperties) {

        this.connection = connection;
        this.producerProperties = producerProperties;
        this.consumerStreamProperties =  consumerStreamProperties;
    }

    /**
     * Returns an instance of the default Kafka connection factory.
     *
     * @param connection the Kafka connection.
     * @param kafkaConfig the Kafka configuration settings.
     * @param clientId the client ID.
     * @return an Kafka connection factory.
     */
    static DefaultKafkaConnectionFactory getInstance(final Connection connection, final KafkaConfig kafkaConfig,
            final String clientId) {
        final ProducerPropertiesFactory producerPropertiesFactory =
                ProducerPropertiesFactory.getInstance(connection, kafkaConfig, clientId);
        final ConsumerPropertiesFactory consumerPropertiesFactory = ConsumerPropertiesFactory.getInstance(connection);

        return new DefaultKafkaConnectionFactory(connection, producerPropertiesFactory.getProducerProperties(),
                consumerPropertiesFactory.getConsumerProperties());
    }

    @Override
    public ConnectionId connectionId() {
        return connection.getId();
    }

    @Override
    public Producer<String, String> newProducer() {
        return new KafkaProducer<>(producerProperties, KEY_SERIALIZER, VALUE_SERIALIZER);
    }

    @Override
    public Properties consumerStreamProperties() {
        return consumerStreamProperties;
    }

}
