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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.header;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.acks.FilteredAcknowledgementRequest;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.common.ResponseType;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.PayloadMapping;
import org.eclipse.ditto.model.connectivity.ReplyTarget;
import org.eclipse.ditto.services.connectivity.messaging.AbstractConsumerActorTest;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.MqttSpecificConfig;

import com.hivemq.client.internal.checkpoint.Confirmable;
import com.hivemq.client.internal.mqtt.message.publish.MqttPublish;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.ActorRef;
import akka.actor.Props;

/**
 * Unit test for {@link HiveMqtt5ConsumerActor}.
 */
public final class HiveMqtt5ConsumerActorTest extends AbstractConsumerActorTest<Mqtt5Publish> {

    private final CountDownLatch confirmLatch = new CountDownLatch(1);

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final MqttSpecificConfig SPECIFIC_CONFIG =
            MqttSpecificConfig.fromConnection(TestConstants.createConnection(CONNECTION_ID));

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor,
            final Set<AcknowledgementRequest> acknowledgementRequests) {
        return HiveMqtt5ConsumerActor.props(CONNECTION_ID, mappingActor, ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(ENFORCEMENT)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .acknowledgementRequests(FilteredAcknowledgementRequest.of(acknowledgementRequests, null))
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build(), false, SPECIFIC_CONFIG);
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

    @Override
    protected Props getConsumerActorProps(final ActorRef mappingActor, final PayloadMapping payloadMapping) {
        return HiveMqtt5ConsumerActor.props(CONNECTION_ID, mappingActor, ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .enforcement(ENFORCEMENT)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .payloadMapping(payloadMapping)
                .replyTarget(ReplyTarget.newBuilder()
                        .address("foo")
                        .expectedResponseTypes(ResponseType.ERROR, ResponseType.RESPONSE, ResponseType.NACK)
                        .build())
                .build(), false, SPECIFIC_CONFIG);
    }

    @Override
    protected Mqtt5Publish getInboundMessage(final String payload, final Map.Entry<String, Object> header) {
        final Mqtt5Publish mqtt5Publish = Mqtt5Publish.builder()
                .topic("org.eclipse.ditto.test/testThing/things/twin/commands/modify")
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .contentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)
                .userProperties(Mqtt5UserProperties.builder()
                        .add(header.getKey(), header.getValue().toString())
                        .build())
                .responseTopic(REPLY_TO_HEADER.getValue())
                .build();
        final MqttPublish mqttPublish = (MqttPublish) mqtt5Publish;
        return mqttPublish.withConfirmable(new MockConfirmable());
    }

    @Override
    protected void verifyMessageSettlement(final boolean isSuccessExpected, final boolean shouldRedeliver)
            throws Exception {
        if (isSuccessExpected) {
            assertThat(confirmLatch.await(3L, TimeUnit.SECONDS))
                    .describedAs("Expect MQTT5 confirmation")
                    .isTrue();
        }
        // Negative MQTT5 acks not supported by Ditto
    }

    private final class MockConfirmable implements Confirmable {

        @Override
        public long getId() {
            return 1234L;
        }

        @Override
        public boolean confirm() {
            confirmLatch.countDown();
            return true;
        }
    }
}
