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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.awaitility.Awaitility;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.kafka.KafkaPublisherActor}.
 */
public class KafkaPublisherActorTest extends AbstractPublisherActorTest {

    private static final String OUTBOUND_ADDRESS = "anyTopic/keyA";

    private final Queue<ProducerRecord<String, String>> received = new ConcurrentLinkedQueue<>();
    private KafkaConnectionFactory connectionFactory;

    @Override
    @SuppressWarnings("unchecked")
    protected void setupMocks(final TestProbe probe) {
        connectionFactory = mock(KafkaConnectionFactory.class);
        final Producer<String, String> mockProducer = mock(Producer.class);
        when(mockProducer.send(any(), any()))
                .thenAnswer(invocationOnMock -> {
                    final ProducerRecord<String, String> record = invocationOnMock.getArgument(0);
                    final RecordMetadata dummyMetadata = new RecordMetadata(null, 0L, 0L, 0L, 0L, 0, 0);
                    invocationOnMock.getArgument(1, Callback.class).onCompletion(dummyMetadata, null);
                    received.add(record);
                    return null;
                });
        when(connectionFactory.newProducer()).thenReturn(mockProducer);
    }

    @Override
    protected Props getPublisherActorProps() {
        return KafkaPublisherActor.props(TestConstants.createConnection(), connectionFactory, false);
    }

    @Override
    protected void verifyPublishedMessage() {
        Awaitility.await().until(() -> !received.isEmpty());
        final ProducerRecord<String, String> record = checkNotNull(received.poll());
        assertThat(received).isEmpty();
        assertThat(record).isNotNull();
        assertThat(record.topic()).isEqualTo("anyTopic");
        assertThat(record.key()).isEqualTo("keyA");
        assertThat(record.value()).isEqualTo("payload");
        final List<Header> headers = Arrays.asList(record.headers().toArray());
        shouldContainHeader(headers, "thing_id", TestConstants.Things.THING_ID.toString());
        shouldContainHeader(headers, "suffixed_thing_id", TestConstants.Things.THING_ID + ".some.suffix");
        shouldContainHeader(headers, "prefixed_thing_id", "some.prefix." + TestConstants.Things.THING_ID);
        shouldContainHeader(headers, "eclipse", "ditto");
        shouldContainHeader(headers, "device_id", TestConstants.Things.THING_ID.toString());
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() {
        Awaitility.await().until(() -> !received.isEmpty());
        final ProducerRecord<String, String> record = checkNotNull(received.poll());
        assertThat(received).isEmpty();
        assertThat(record.topic()).isEqualTo("replyTarget");
        assertThat(record.key()).isEqualTo("thing:id");
        final List<Header> headers = Arrays.asList(record.headers().toArray());
        shouldContainHeader(headers, "correlation-id", TestConstants.CORRELATION_ID);
        shouldContainHeader(headers, "mappedHeader2", "thing:id");
    }

    @Override
    protected void publisherCreated(final TestKit kit, final ActorRef publisherActor) {
        kit.expectMsgClass(Status.Success.class);
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    @Override
    protected String getOutboundAddress() {
        return OUTBOUND_ADDRESS;
    }

    private void shouldContainHeader(final List<Header> headers, final String key, final String value) {
        final RecordHeader expectedHeader = new RecordHeader(key, value.getBytes(StandardCharsets.US_ASCII));
        assertThat(headers).contains(expectedHeader);
    }

}
