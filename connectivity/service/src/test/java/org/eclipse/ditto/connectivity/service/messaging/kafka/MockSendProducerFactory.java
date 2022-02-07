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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import akka.kafka.ProducerMessage;
import akka.kafka.javadsl.SendProducer;
import akka.kafka.testkit.ProducerResultFactory;

/**
 * Creates mock {@code SendProducer}s.
 */
final class MockSendProducerFactory implements SendProducerFactory {

    private final String targetTopic;
    private final Queue<ProducerRecord<String, ByteBuffer>> published;
    @Nullable private final RuntimeException exception;
    final boolean shouldThrowException;
    private final boolean blocking;
    private final AtomicBoolean wait = new AtomicBoolean();
    private final AtomicInteger counter = new AtomicInteger();

    private MockSendProducerFactory(final String targetTopic,
            final Queue<ProducerRecord<String, ByteBuffer>> published,
            final boolean blocking,
            final boolean slow,
            @Nullable final RuntimeException exception,
            final boolean shouldThrowException) {

        this.targetTopic = targetTopic;
        this.published = published;
        this.blocking = blocking;
        wait.set(slow);
        this.exception = exception;
        this.shouldThrowException = shouldThrowException;
    }

    public static MockSendProducerFactory getInstance(
            final String targetTopic, final Queue<ProducerRecord<String, ByteBuffer>> published) {

        return new MockSendProducerFactory(targetTopic, published, false, false, null, false);
    }

    public static MockSendProducerFactory getInstance(final String targetTopic,
            final Queue<ProducerRecord<String, ByteBuffer>> published,
            final RuntimeException exception,
            final boolean shouldThrowException) {

        return new MockSendProducerFactory(targetTopic, published, false, false, exception, shouldThrowException);
    }

    /**
     * Never publishes any message, returns futures that never complete.
     */
    public static MockSendProducerFactory getBlockingInstance(final String targetTopic,
            final Queue<ProducerRecord<String, ByteBuffer>> published) {

        return new MockSendProducerFactory(targetTopic, published, true, false, null, false);
    }

    /**
     * Blocks 1 second for the first message published. Continues to operate normally afterwards.
     */
    public static MockSendProducerFactory getSlowStartInstance(final String targetTopic,
            final Queue<ProducerRecord<String, ByteBuffer>> published) {
        return new MockSendProducerFactory(targetTopic, published, false, true, null, false);
    }

    @Override
    public SendProducer<String, ByteBuffer> newSendProducer() {
        final SendProducer<String, ByteBuffer> producer = mock(SendProducer.class);
        if (blocking) {
            when(producer.sendEnvelope(any(ProducerMessage.Envelope.class)))
                    .thenAnswer(invocationOnMock -> {
                        // simulate slow publishing
                        Thread.sleep(1000);
                        // always return uncompleted future
                        return new CompletableFuture<>();
                    });
        } else if (exception == null) {
            when(producer.sendEnvelope(any(ProducerMessage.Envelope.class)))
                    .thenAnswer(invocationOnMock -> {

                        // simulate slow start producer that blocks 1 sec for the very first message published
                        if (wait.getAndSet(false)) {
                            Thread.sleep(1000);
                        }

                        final ProducerMessage.Envelope<String, ByteBuffer, CompletableFuture<RecordMetadata>> envelope =
                                invocationOnMock.getArgument(0);
                        final RecordMetadata dummyMetadata =
                                new RecordMetadata(new TopicPartition(targetTopic, 5),
                                        0L, 0L, 0L, 0L, 0, 0);
                        final ProducerMessage.Message<String, ByteBuffer, CompletableFuture<RecordMetadata>> message =
                                (ProducerMessage.Message<String, ByteBuffer, CompletableFuture<RecordMetadata>>) envelope;
                        published.offer(message.record());

                        return CompletableFuture.completedStage(ProducerResultFactory.result(dummyMetadata, message));
                    });
        } else if (!shouldThrowException){
            when(producer.sendEnvelope(any(ProducerMessage.Envelope.class)))
                    .thenReturn(CompletableFuture.failedStage(exception));
        } else {
            if (counter.get() == 0) {
                when(producer.sendEnvelope(any(ProducerMessage.Envelope.class)))
                        .thenThrow(exception);
                counter.getAndIncrement();
            } else {
                when(producer.sendEnvelope(any(ProducerMessage.Envelope.class)))
                        .thenReturn(CompletableFuture.failedStage(exception));
            }
        }

        return producer;
    }
}
