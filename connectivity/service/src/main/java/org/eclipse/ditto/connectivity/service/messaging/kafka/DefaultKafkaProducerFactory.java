/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import akka.actor.ActorSystem;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.SendProducer;

/**
 * Default implementation of {@link org.eclipse.ditto.connectivity.service.messaging.kafka.KafkaProducerFactory}.
 */
final class DefaultKafkaProducerFactory implements KafkaProducerFactory {

    private final ProducerSettings<String, String> producerSettings;
    private final ActorSystem actorSystem;


    private DefaultKafkaProducerFactory(final ProducerSettings<String, String> producerSettings,
            final ActorSystem actorSystem) {
        this.producerSettings = producerSettings;
        this.actorSystem = actorSystem;
    }

    /**
     * Returns an instance of the default Kafka connection factory.
     *
     * @param producerSettings settings of the created producer.
     * @param actorSystem the actor system.
     * @return an Kafka connection factory.
     */
    static DefaultKafkaProducerFactory getInstance(final ProducerSettings<String, String> producerSettings,
            final ActorSystem actorSystem) {
        return new DefaultKafkaProducerFactory(producerSettings, actorSystem);
    }

    @Override
    public SendProducer<String, String> newProducer() {
        return new SendProducer<>(producerSettings, actorSystem);
    }

}
