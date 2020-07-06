/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Unit test for {@link HiveMqtt3ConsumerActor}.
 */
public final class HiveMqtt3ConsumerActorTest extends AbstractConsumerActorTest<Mqtt3Publish> {

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final MqttSpecificConfig SPECIFIC_CONFIG =
            MqttSpecificConfig.fromConnection(TestConstants.createConnection(CONNECTION_ID));

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        return HiveMqtt3ConsumerActor.props(CONNECTION_ID, mappingActor, ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(TestConstants.MQTT3_HEADER_MAPPING)
                .acknowledgementRequests(acknowledgementRequests)
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build(), false, SPECIFIC_CONFIG);
    }

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping) {
        return HiveMqtt3ConsumerActor.props(CONNECTION_ID, mappingActor, ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(TestConstants.MQTT3_HEADER_MAPPING)
                .payloadMapping(payloadMapping)
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build(), false, SPECIFIC_CONFIG);
    }

    @Override
    protected Mqtt3Publish getInboundMessage(final String payload, final Map.Entry<String, Object> header) {
        return Mqtt3Publish.builder()
                .topic("org.eclipse.ditto.test/testThing/things/twin/commands/modify")
                .qos(MqttQos.AT_MOST_ONCE)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @Override
    protected void verifyMessageSettlement(final boolean isSuccessExpected, final boolean shouldRedeliver) {
    }

    @Override
    public void testInboundMessageWithHeaderMappingThrowsUnresolvedPlaceholderException() {
    }

    @Override
    public void testInboundMessageFails() {
    }

    @Override
    public void testInboundMessageFailsIfHeaderIsMissing() {
    }

    @Override
    protected void testHeaderMapping() {
        testInboundMessage(header("device_id", TestConstants.Things.THING_ID), true, msg -> {
            assertThat(msg.getDittoHeaders()).containsEntry("mqtt.qos", "0");
        }, response -> fail("not expected"));
    }
}
