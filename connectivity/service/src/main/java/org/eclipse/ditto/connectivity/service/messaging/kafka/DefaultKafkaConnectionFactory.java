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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;

/**
 * Creates Kafka sinks.
 */
final class DefaultKafkaConnectionFactory implements KafkaConnectionFactory {

    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    private final Connection connection;
    private final Map<String, Object> properties;

    private DefaultKafkaConnectionFactory(final Connection connection, final Map<String, Object> producerProperties) {

        this.connection = connection;
        properties = producerProperties;
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
        final ProducerPropertiesFactory settingsFactory =
                ProducerPropertiesFactory.getInstance(connection, kafkaConfig, clientId);

        return new DefaultKafkaConnectionFactory(connection, settingsFactory.getProducerProperties());
    }

    @Override
    public EntityId connectionId() {
        return connection.getId();
    }

    @Override
    public org.apache.kafka.clients.producer.Producer<String, String> newProducer() {
        return new KafkaProducer<>(properties, KEY_SERIALIZER, VALUE_SERIALIZER);
    }

}
