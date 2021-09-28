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

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.AcknowledgeableMessage;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import akka.NotUsed;
import akka.actor.ActorSystem;
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
    private Source<ConsumerRecord<String, String>, Consumer.Control> source;
    private Sink<AcknowledgeableMessage, NotUsed> inboundMappingSink;
    private final AtomicReference<BoundedSourceQueue<ConsumerRecord<String, String>>> sourceQueue =
            new AtomicReference<>();
    private final AtomicReference<TestSubscriber.Probe<AcknowledgeableMessage>> inboundSinkProbe =
            new AtomicReference<>();

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem");
        final Consumer.Control control = mock(Consumer.Control.class);
        source = Source.<ConsumerRecord<String, String>>queue(1)
                .mapMaterializedValue(queue -> {
                    sourceQueue.set(queue);
                    return control;
                });
        inboundMappingSink = TestSink.<AcknowledgeableMessage>create(actorSystem).mapMaterializedValue(probe -> {
            // Will be set on each consumed message.
            inboundSinkProbe.set(probe);
            return NotUsed.getInstance();
        });
    }

    @After
    public void tearDown() {
        actorSystem.terminate();
    }

    @Test
    public void isImmutable() {
        assertInstancesOf(AtMostOnceConsumerStream.class,
                areImmutable(),
                provided(ConnectionMonitor.class, Sink.class, Materializer.class, Consumer.DrainingControl.class)
                        .areAlsoImmutable());
    }

    @Test
    public void appliesBackPressureWhenMessagesAreNotAcknowledged() throws InterruptedException {
        new TestKit(actorSystem) {{
            /*
             * Given we have a kafka source which emits records that are all transformed to External messages.
             */
            final ConsumerRecord<String, String> consumerRecord =
                    new ConsumerRecord<>("topic", 1, 1, Instant.now().toEpochMilli(), TimestampType.LOG_APPEND_TIME,
                            -1L, NULL_SIZE, NULL_SIZE, "Key", "Value", new RecordHeaders());
            final AtMostOnceKafkaConsumerSourceSupplier sourceSupplier =
                    mock(AtMostOnceKafkaConsumerSourceSupplier.class);
            when(sourceSupplier.get()).thenReturn(source);
            final KafkaMessageTransformer messageTransformer = mock(KafkaMessageTransformer.class);
            final TransformationResult result = TransformationResult.successful(mock(ExternalMessage.class));
            when(messageTransformer.transform(ArgumentMatchers.<ConsumerRecord<String, String>>any())).thenReturn(
                    result);
            final ConnectionMonitor connectionMonitor = mock(ConnectionMonitor.class);
            final int maxInflight = 3;
            final Materializer materializer = Materializer.createMaterializer(actorSystem);
            final Sink<DittoRuntimeException, TestSubscriber.Probe<DittoRuntimeException>> dreSink =
                    TestSink.create(actorSystem);

            // When starting the stream
            new AtMostOnceConsumerStream(sourceSupplier, maxInflight, messageTransformer, false, materializer,
                    connectionMonitor, inboundMappingSink, dreSink);

            // Then we can offer those records and they are processed in parallel to the maximum of 'maxInflight' + 1
            for (int i = 0; i < maxInflight + 1; i++) { // +1 because one message goes to the buffer
                assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.enqueued());
                final TestSubscriber.Probe<AcknowledgeableMessage> inboundMappingSinkProbe =
                        getInboundMappingSinkPropeAfterSleep();
                inboundMappingSinkProbe.ensureSubscription();
                inboundMappingSinkProbe.requestNext();
                inboundMappingSinkProbe.expectComplete();
            }

            // Further messages are going to the buffer but are not forwarded to the mapping sink.
            final int bufferSize = 4; // SourceQueue creates a source with buffer size 4.
            for (int i = 0; i < bufferSize - 1; i++) { // -1 because one message already went to the buffer before
                assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.enqueued());
                final TestSubscriber.Probe<AcknowledgeableMessage> inboundMappingSinkProbe =
                        getInboundMappingSinkPropeAfterSleep();
                inboundMappingSinkProbe.ensureSubscription();
                inboundMappingSinkProbe.request(1);
                inboundMappingSinkProbe.expectNoMessage();
            }

            // Buffer is full. No messages can be offered anymore. Backpressure applies.
            assertThat(sourceQueue.get().offer(consumerRecord)).isEqualTo(QueueOfferResult.dropped());
        }};
    }

    private TestSubscriber.Probe<AcknowledgeableMessage> getInboundMappingSinkPropeAfterSleep()
            throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        return inboundSinkProbe.get();
    }

}
