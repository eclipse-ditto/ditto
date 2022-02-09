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

import static org.apache.kafka.clients.consumer.ConsumerRecord.NULL_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.Pair;
import akka.kafka.javadsl.Consumer;
import akka.stream.BoundedSourceQueue;
import akka.stream.Materializer;
import akka.stream.QueueOfferResult;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.testkit.TestSubscriber;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;

public final class AtMostOnceConsumerStreamTest {

    private ActorSystem actorSystem;
    private Source<ConsumerRecord<String, ByteBuffer>, Consumer.Control> source;
    private Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink;
    private final AtomicReference<BoundedSourceQueue<ConsumerRecord<String, ByteBuffer>>> sourceQueue =
            new AtomicReference<>();
    private TestSubscriber.Probe<AcknowledgeableMessage> inboundSinkProbe;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        final Consumer.Control control = mock(Consumer.Control.class);
        source = Source.<ConsumerRecord<String, ByteBuffer>>queue(1)
                .mapMaterializedValue(queue -> {
                    sourceQueue.set(queue);
                    return control;
                });
        final Sink<AcknowledgeableMessage, TestSubscriber.Probe<AcknowledgeableMessage>> sink =
                TestSink.probe(actorSystem);
        final Pair<TestSubscriber.Probe<AcknowledgeableMessage>, Sink<AcknowledgeableMessage, NotUsed>> sinkPair =
                sink.preMaterialize(actorSystem);
        inboundSinkProbe = sinkPair.first();
        inboundMappingSink = sinkPair.second();
    }

    @After
    public void tearDown() {
        actorSystem.terminate();
    }

    @Test
    public void isImmutable() {
        assertInstancesOf(AtMostOnceConsumerStream.class,
                areImmutable(),
                provided(ConnectionMonitor.class, Sink.class, Materializer.class, Consumer.DrainingControl.class,
                        KafkaConsumerMetrics.class)
                        .areAlsoImmutable());
    }

    @Test
    public void appliesBackPressureWhenMessagesAreNotAcknowledged() {
        new TestKit(actorSystem) {{
            /*
             * Given we have a kafka source which emits records that are all transformed to External messages.
             */
            final ConsumerRecord<String, ByteBuffer> consumerRecord =
                    new ConsumerRecord<>("topic", 1, 1, Instant.now().toEpochMilli(), TimestampType.LOG_APPEND_TIME,
                            -1L, NULL_SIZE, NULL_SIZE, "Key", ByteBufferUtils.fromUtf8String("Value"),
                            new RecordHeaders());
            final AtMostOnceKafkaConsumerSourceSupplier sourceSupplier =
                    mock(AtMostOnceKafkaConsumerSourceSupplier.class);
            when(sourceSupplier.get()).thenReturn(source);
            final KafkaMessageTransformer messageTransformer = mock(KafkaMessageTransformer.class);
            final TransformationResult result = TransformationResult.successful(mock(ExternalMessage.class));
            when(messageTransformer.transform(ArgumentMatchers.<ConsumerRecord<String, ByteBuffer>>any()))
                    .thenReturn(result);
            final ConnectionMonitor connectionMonitor = mock(ConnectionMonitor.class);
            final int maxInflight = TestConstants.KAFKA_THROTTLING_CONFIG.getMaxInFlight();
            final Materializer materializer = Materializer.createMaterializer(actorSystem);
            final Sink<DittoRuntimeException, TestSubscriber.Probe<DittoRuntimeException>> dreSink =
                    TestSink.create(actorSystem);

            // When starting the stream
            new AtMostOnceConsumerStream(sourceSupplier, TestConstants.KAFKA_THROTTLING_CONFIG, messageTransformer,
                    false, materializer,
                    connectionMonitor, inboundMappingSink, dreSink,
                    ConnectionId.generateRandom(),
                    "someUniqueId");

            inboundSinkProbe.ensureSubscription();
            // Then we can offer those records and they are processed in parallel to the maximum of 'maxInflight'
            for (int i = 0; i < maxInflight + 1; i++) {
                assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.enqueued());
                inboundSinkProbe.request(1);
                inboundSinkProbe.expectNext();
            }

            /*
             * Further messages are queued but not forwarded to the mapping sink. I can't fully explain why it is 3.
             * This depends on the test setup of the SourceQueue.
             */
            final int bufferSize = 3;
            for (int i = 0; i < bufferSize; i++) {
                assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.enqueued());
                // This is done to verify that no matter that inboundSinkProbe is requesting new Elements. The mapAsync stage is blocking further elements to be processed.
                inboundSinkProbe.request(1);
                inboundSinkProbe.expectNoMessage();
            }

            // Buffer is full. No messages can be offered anymore. Backpressure applies.
            assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.dropped());
        }};
    }

    @Test
    public void filtersExpiredMessages() {
        new TestKit(actorSystem) {{
            /*
             * Given we have a kafka source which emits records that are all transformed to External messages.
             */
            final ConsumerRecord<String, ByteBuffer> consumerRecord =
                    new ConsumerRecord<>("topic", 1, 1, Instant.now().toEpochMilli(), TimestampType.LOG_APPEND_TIME,
                            -1L, NULL_SIZE, NULL_SIZE, "Key", ByteBufferUtils.fromUtf8String("Value"),
                            new RecordHeaders());
            final AtMostOnceKafkaConsumerSourceSupplier sourceSupplier =
                    mock(AtMostOnceKafkaConsumerSourceSupplier.class);
            when(sourceSupplier.get()).thenReturn(source);
            final KafkaMessageTransformer messageTransformer = mock(KafkaMessageTransformer.class);
            final ExternalMessage message = mock(ExternalMessage.class);
            when(message.getHeaders()).thenReturn(Map.of("creation-time", "0", "ttl", "1000"));
            final TransformationResult result = TransformationResult.successful(message);
            when(messageTransformer.transform(ArgumentMatchers.<ConsumerRecord<String, ByteBuffer>>any()))
                    .thenReturn(result);
            final ConnectionMonitor connectionMonitor = mock(ConnectionMonitor.class);
            final Materializer materializer = Materializer.createMaterializer(actorSystem);
            final Sink<DittoRuntimeException, TestSubscriber.Probe<DittoRuntimeException>> dreSink =
                    TestSink.create(actorSystem);

            // When starting the stream
            new AtMostOnceConsumerStream(sourceSupplier, TestConstants.KAFKA_THROTTLING_CONFIG, messageTransformer,
                    false, materializer,
                    connectionMonitor, inboundMappingSink, dreSink,
                    ConnectionId.generateRandom(),
                    "someUniqueId");

            assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.enqueued());
            inboundSinkProbe.request(1);
            inboundSinkProbe.expectNoMessage();
        }};
    }

}
