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
package org.eclipse.ditto.connectivity.service.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.header;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.PayloadMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractConsumerActorWithAcknowledgementsTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.junit.Test;
import org.mockito.Mockito;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link RabbitMQConsumerActor}.
 */
public final class RabbitMQConsumerActorTest extends AbstractConsumerActorWithAcknowledgementsTest<Delivery> {

    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final Envelope ENVELOPE = new Envelope(1, false, "inbound", "ditto");

    private final Channel channel = Mockito.mock(Channel.class);


    @Test
    public void stopConsumingOnRequest() throws Exception {
        new TestKit(actorSystem) {{
            final TestProbe proxyActor = TestProbe.apply(actorSystem);
            final TestProbe clientActor = TestProbe.apply(actorSystem);
            final Sink<Object, NotUsed> inboundMappingSink =
                    setupInboundMappingSink(clientActor.ref(), proxyActor.ref());
            final var payloadMapping = ConnectivityModelFactory.newPayloadMapping("ditto", "ditto");

            final ActorRef underTest = actorSystem.actorOf(getConsumerActorProps(inboundMappingSink, payloadMapping));

            underTest.tell(RabbitMQConsumerActor.Control.STOP_CONSUMER, getRef());
            expectMsg(Done.getInstance());
            verify(channel).close();
        }};

    }

    @Override
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        return RabbitMQConsumerActor.props("rmq-consumer", inboundMappingSink,
                ConnectivityModelFactory.newSourceBuilder()
                        .address("rmq-consumer")
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .enforcement(ENFORCEMENT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .acknowledgementRequests(FilteredAcknowledgementRequest.of(acknowledgementRequests, null))
                        .replyTarget(ReplyTarget.newBuilder()
                                .address("foo")
                                .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                                .build())
                        .build(),
                channel,
                CONNECTION,
                mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()));
    }

    @Override
    protected Props getConsumerActorProps(final Sink<Object, NotUsed> inboundMappingSink,
            final PayloadMapping payloadMapping) {
        return RabbitMQConsumerActor.props("rmq-consumer", inboundMappingSink,
                ConnectivityModelFactory.newSourceBuilder()
                        .address("rmq-consumer")
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .enforcement(ENFORCEMENT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .payloadMapping(payloadMapping)
                        .replyTarget(ReplyTarget.newBuilder()
                                .address("foo")
                                .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                                .build())
                        .build(),
                channel,
                CONNECTION,
                mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()));
    }

    @Override
    protected Delivery getInboundMessage(final String payload, final Map.Entry<String, Object> header) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(REPLY_TO_HEADER.getKey(), REPLY_TO_HEADER.getValue());
        headers.put(header.getKey(), header.getValue());

        return new Delivery(ENVELOPE,
                new AMQP.BasicProperties.Builder()
                        .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                        .headers(headers)
                        .replyTo(REPLY_TO_HEADER.getValue()).build(),
                payload.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void verifyMessageSettlement(final TestKit testKit, final boolean isSuccessExpected,
            final boolean shouldRedeliver)
            throws Exception {
        if (isSuccessExpected) {
            Mockito.verify(channel, Mockito.timeout(3000L)).basicAck(anyLong(), eq(false));
        } else {
            // expect no redelivery due to DittoRuntimeException
            Mockito.verify(channel, Mockito.timeout(3000L)).basicNack(anyLong(), eq(false), eq(shouldRedeliver));
        }
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
        }, response -> fail("not expected"));
    }
}
