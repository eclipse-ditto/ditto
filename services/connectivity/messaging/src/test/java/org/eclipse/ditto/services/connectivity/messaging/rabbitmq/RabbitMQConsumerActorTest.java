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
package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.mockito.Mockito;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link RabbitMQConsumerActor}.
 */
public final class RabbitMQConsumerActorTest extends AbstractConsumerActorTest<Delivery> {

    private static final Connection CONNECTION = TestConstants.createConnection();
    private static final Envelope ENVELOPE = new Envelope(1, false, "inbound", "ditto");

    private final Channel channel = Mockito.mock(Channel.class);

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        return RabbitMQConsumerActor.props("rmq-consumer", mappingActor,
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
                CONNECTION);
    }

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping) {
        return RabbitMQConsumerActor.props("rmq-consumer", mappingActor,
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
                CONNECTION);
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
