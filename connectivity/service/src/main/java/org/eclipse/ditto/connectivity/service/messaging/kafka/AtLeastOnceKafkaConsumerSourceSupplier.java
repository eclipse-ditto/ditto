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
import java.util.function.Supplier;

import org.apache.kafka.clients.consumer.ConsumerConfig;

import akka.kafka.AutoSubscription;
import akka.kafka.ConsumerMessage;
import akka.kafka.ConsumerSettings;
import akka.kafka.Subscriptions;
import akka.kafka.javadsl.Consumer;
import akka.stream.javadsl.Source;

/**
 * Supplies a {@link Source} of {@link akka.kafka.ConsumerMessage.CommittableMessage}s retaining a
 * {@link akka.kafka.javadsl.Consumer.Control} in order to be able to shutdown/terminate Kafka consumption.
 */
class AtLeastOnceKafkaConsumerSourceSupplier
        implements Supplier<Source<ConsumerMessage.CommittableMessage<String, ByteBuffer>, Consumer.Control>> {

    private final PropertiesFactory propertiesFactory;
    private final String sourceAddress;
    private final boolean dryRun;

    AtLeastOnceKafkaConsumerSourceSupplier(
            final PropertiesFactory propertiesFactory, final String sourceAddress, final boolean dryRun) {
        this.propertiesFactory = propertiesFactory;
        this.sourceAddress = sourceAddress;
        this.dryRun = dryRun;
    }

    @Override
    public Source<ConsumerMessage.CommittableMessage<String, ByteBuffer>, Consumer.Control> get() {
        final ConsumerSettings<String, ByteBuffer> consumerSettings = propertiesFactory.getConsumerSettings(dryRun)
                .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        final AutoSubscription subscription = Subscriptions.topics(sourceAddress);
        return Consumer.committableSource(consumerSettings, subscription);
    }

}
