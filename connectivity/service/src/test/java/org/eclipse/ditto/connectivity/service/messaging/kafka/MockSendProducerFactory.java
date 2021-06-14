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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.mockito.Mockito;

import akka.kafka.javadsl.SendProducer;

/**
 * Creates mock {@code SendProducer}s.
 */
final class MockSendProducerFactory implements SendProducerFactory {

    private final String targetTopic;
    private final Queue<ProducerRecord<String, String>> published;
    @Nullable private final RuntimeException exception;

    private MockSendProducerFactory(final String targetTopic, final Queue<ProducerRecord<String, String>> published,
            @Nullable final RuntimeException exception) {
        this.targetTopic = targetTopic;
        this.published = published;
        this.exception = exception;
    }

    public static MockSendProducerFactory getInstance(
            final String targetTopic, final Queue<ProducerRecord<String, String>> published) {
        return new MockSendProducerFactory(targetTopic, published, null);
    }

    public static MockSendProducerFactory getInstance(final String targetTopic,
            final Queue<ProducerRecord<String, String>> published, final RuntimeException exception) {
        return new MockSendProducerFactory(targetTopic, published, exception);
    }

    @Override
    public SendProducer<String, String> newSendProducer() {
        final SendProducer<String, String> producer = Mockito.mock(SendProducer.class);
        if (exception == null) {
            when(producer.send(any(ProducerRecord.class)))
                    .thenAnswer(invocationOnMock -> {
                        final ProducerRecord<String, String> record = invocationOnMock.getArgument(0);
                        final RecordMetadata dummyMetadata =
                                new RecordMetadata(new TopicPartition(targetTopic, 5), 0L, 0L, 0L, 0L, 0, 0);
                        published.offer(record);
                        return CompletableFuture.completedFuture(dummyMetadata);
                    });
        } else {
            when(producer.send(any(ProducerRecord.class)))
                    .thenAnswer(invocationOnMock -> CompletableFuture.failedFuture(exception));
        }

        return producer;
    }
}
