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
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.KafkaConfig;

/**
 * Default implementation of {@link org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaProducerFactory}.
 */
final class DefaultKafkaProducerFactory implements KafkaProducerFactory {

    private static final Serializer<String> KEY_SERIALIZER = new StringSerializer();
    private static final Serializer<String> VALUE_SERIALIZER = KEY_SERIALIZER;

    private final Map<String, Object> producerProperties;

    private DefaultKafkaProducerFactory(final Map<String, Object> producerProperties) {
        this.producerProperties = producerProperties;
    }

    /**
     * Returns an instance of the default Kafka connection factory.
     *
     * @param propertiesFactory a factory to create kafka client configuration properties.
     * @return an Kafka connection factory.
     */
    static DefaultKafkaProducerFactory getInstance(final PropertiesFactory propertiesFactory) {
        final Map<String, Object> producerProperties = propertiesFactory.getProducerProperties();
        return new DefaultKafkaProducerFactory(producerProperties);
    }

    @Override
    public Producer<String, String> newProducer() {
        return new KafkaProducer<>(producerProperties, KEY_SERIALIZER, VALUE_SERIALIZER);
    }

}
