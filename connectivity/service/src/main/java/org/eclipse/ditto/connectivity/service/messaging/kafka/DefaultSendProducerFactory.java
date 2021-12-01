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

import java.nio.ByteBuffer;

import akka.actor.ActorSystem;
import akka.kafka.ProducerSettings;
import akka.kafka.javadsl.SendProducer;

/**
 * Default implementation of {@link SendProducerFactory}.
 */
final class DefaultSendProducerFactory implements SendProducerFactory {

    private final ProducerSettings<String, ByteBuffer> producerSettings;
    private final ActorSystem actorSystem;

    private DefaultSendProducerFactory(final ProducerSettings<String, ByteBuffer> producerSettings,
            final ActorSystem actorSystem) {

        this.producerSettings = producerSettings;
        this.actorSystem = actorSystem;
    }

    /**
     * Returns an instance of the default SendProducerFactory.
     *
     * @param producerSettings settings of the created producer.
     * @param actorSystem the actor system
     * @return a Kafka SendProducerFactory.
     */
    static DefaultSendProducerFactory getInstance(final ProducerSettings<String, ByteBuffer> producerSettings,
            final ActorSystem actorSystem) {

        return new DefaultSendProducerFactory(producerSettings, actorSystem);
    }

    @Override
    public SendProducer<String, ByteBuffer> newSendProducer() {
        return new SendProducer<>(producerSettings, actorSystem);
    }

}
