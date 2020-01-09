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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Unit test for {@link RabbitMQConsumerActor}.
 */
public final class RabbitMQConsumerActorTest extends AbstractConsumerActorTest<Delivery> {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final Envelope ENVELOPE = new Envelope(1, false, "inbound", "ditto");

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping) {
        return RabbitMQConsumerActor.props("rmq-consumer", mappingActor,
                ConnectivityModelFactory.newSourceBuilder()
                        .address("rmq-consumer")
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .enforcement(ENFORCEMENT)
                        .headerMapping(TestConstants.HEADER_MAPPING)
                        .payloadMapping(payloadMapping)
                        .build(),
                CONNECTION_ID);
    }

    @Override
    protected Delivery getInboundMessage(final Map.Entry<String, Object> header) {
        final Map<String, Object> headers = new HashMap<>();
        headers.put(REPLY_TO_HEADER.getKey(), REPLY_TO_HEADER.getValue());
        headers.put(header.getKey(), header.getValue());

        return new Delivery(ENVELOPE,
                new AMQP.BasicProperties.Builder()
                        .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                        .headers(headers)
                        .replyTo(REPLY_TO_HEADER.getValue()).build(),
                TestConstants.modifyThing().getBytes(StandardCharsets.UTF_8));
    }

}
