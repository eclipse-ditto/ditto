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
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.header;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.service.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Before;
import org.mockito.Mockito;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.kafka.javadsl.Consumer;
import akka.stream.BoundedSourceQueue;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Tests {@code KafkaConsumerActor}.
 */
public class KafkaConsumerActorTest extends AbstractConsumerActorTest<ConsumerRecord<String, String>> {

    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final String TOPIC = "kafka.topic";
    private static final long TIMESTAMP = System.currentTimeMillis();
    private static final String KEY = "key";
    private static final String CUSTOM_TOPIC = "the.topic";
    private static final String CUSTOM_KEY = "the.key";
    private static final String CUSTOM_TIMESTAMP = "the.timestamp";

    private BoundedSourceQueue<ConsumerRecord<String, String>> sourceQueue;
    private Source<ConsumerRecord<String, String>, Consumer.Control> source;

    @Before
    public void initKafka() {
        final Consumer.Control control = Mockito.mock(Consumer.Control.class);
        final Pair<BoundedSourceQueue<ConsumerRecord<String, String>>, Source<ConsumerRecord<String, String>, NotUsed>>
                sourcePair = Source.<ConsumerRecord<String, String>>queue(20)
                .preMaterialize(Materializer.createMaterializer(actorSystem));
        sourceQueue = sourcePair.first();
        source = sourcePair.second().mapMaterializedValue(notused -> control);
    }

    @Override
    protected void stopConsumerActor(final ActorRef underTest) {
        underTest.tell(KafkaConsumerActor.GracefulStop.INSTANCE, ActorRef.noSender());
    }

    @Override
    protected void testHeaderMapping() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), true, msg -> {
            assertThat(msg.getDittoHeaders()).containsEntry("eclipse", "ditto");
            assertThat(msg.getDittoHeaders()).containsEntry("thing_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("device_id", TestConstants.Things.THING_ID.toString());
            assertThat(msg.getDittoHeaders()).containsEntry("prefixed_thing_id",
                    "some.prefix." + TestConstants.Things.THING_ID);
            assertThat(msg.getDittoHeaders()).containsEntry("suffixed_thing_id",
                    TestConstants.Things.THING_ID + ".some.suffix");

            assertThat(msg.getDittoHeaders()).containsEntry(CUSTOM_TOPIC, TOPIC);
            assertThat(msg.getDittoHeaders()).containsEntry(CUSTOM_KEY, KEY);
            assertThat(msg.getDittoHeaders()).containsEntry(CUSTOM_TIMESTAMP, Long.toString(TIMESTAMP));
        }, response -> fail("not expected"));
    }

    @Override
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping) {

        final Map<String, String> map = new HashMap<>(TestConstants.HEADER_MAPPING.getMapping());
        map.putAll(Map.of(
                CUSTOM_TOPIC, "{{ header:kafka.topic }}",
                CUSTOM_KEY, "{{ header:kafka.key }}",
                CUSTOM_TIMESTAMP, "{{ header:kafka.timestamp }}"
        ));
        final HeaderMapping mappingWithSpecialKafkaHeaders = ConnectivityModelFactory.newHeaderMapping(map);

        return KafkaConsumerActor.props(CONNECTION, () -> source, "kafka", inboundMappingSink,
                ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .address("kafka")
                        .enforcement(ENFORCEMENT)
                        .headerMapping(mappingWithSpecialKafkaHeaders)
                        .payloadMapping(payloadMapping)
                        .replyTarget(ReplyTarget.newBuilder()
                                .address("foo")
                                .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                                .build())
                        .build(),
                false);
    }

    @Override
    protected void consumeMessage(final ActorRef consumerActor, final ConsumerRecord<String, String> inboundMessage,
            final ActorRef sender) {
        sourceQueue.offer(inboundMessage);
    }

    @Override
    protected ConsumerRecord<String, String> getInboundMessage(final String payload,
            final Map.Entry<String, Object> header) {
        final Headers headers = new RecordHeaders()
                .add(toRecordHeader(header))
                .add(toRecordHeader(REPLY_TO_HEADER));
        return new ConsumerRecord<>(TOPIC, 1, 1, TIMESTAMP, TimestampType.LOG_APPEND_TIME,
                -1L, NULL_SIZE, NULL_SIZE, KEY, payload, headers);
    }

    private RecordHeader toRecordHeader(final Map.Entry<String, ?> header) {
        return new RecordHeader(header.getKey(), header.getValue().toString().getBytes());
    }

}
