/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.eclipse.ditto.base.model.common.ByteBufferUtils;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.AbstractMqttClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttServerRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

public final class HiveMqtt5ClientActorTest extends AbstractMqttClientActorTest<Mqtt5Publish> {

    private static final TestConstants.FreePort FREE_PORT = new TestConstants.FreePort();

    @ClassRule
    public static final MqttServerRule MQTT_SERVER = new MqttServerRule(FREE_PORT.getPort());

    private MockHiveMqtt5ClientFactory mockHiveMqtt5ClientFactory;

    @Before
    public void initClient() {
        mockHiveMqtt5ClientFactory = new MockHiveMqtt5ClientFactory();
    }

    @Override
    protected void expectDisconnectCalled() {
        mockHiveMqtt5ClientFactory.expectDisconnectCalled();
    }

    @Test
    public void testSubscribeFails() {
        new TestKit(actorSystem) {{
            final MockHiveMqtt5ClientFactory clientFactory = mockHiveMqtt5ClientFactory
                    .withTestProbe(getRef())
                    .withFailingSubscribe();

            final Props props =
                    HiveMqtt5ClientActor.props(connection, getRef(), clientFactory, mockConnectionActor.ref(),
                            dittoHeaders, ConfigFactory.empty());
            final ActorRef mqttClientActor = actorSystem.actorOf(props, "mqttClientActor-testSubscribeFails");

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(Duration.ofSeconds(10L), Status.Failure.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return HiveMqtt5ClientActor.props(connection, proxyActor,
                mockHiveMqtt5ClientFactory.withTestProbe(proxyActor), mockConnectionActor.ref(), dittoHeaders,
                ConfigFactory.empty());
    }

    @Override
    protected TestConstants.FreePort getFreePort() {
        return FREE_PORT;
    }

    @Override
    protected Props createFailingClientActor(final ActorRef testProbe) {
        return HiveMqtt5ClientActor.props(connection, testProbe,
                mockHiveMqtt5ClientFactory
                        .withException(new RuntimeException("failed to connect")), mockConnectionActor.ref(),
                dittoHeaders, ConfigFactory.empty());
    }

    @Override
    protected Props createClientActorWithMessages(final Connection connection,
            final ActorRef testProbe,
            final List<Mqtt5Publish> messages) {
        final MockHiveMqtt5ClientFactory clientFactory = mockHiveMqtt5ClientFactory
                .withMessages(messages)
                .withTestProbe(testProbe);
        return HiveMqtt5ClientActor.props(connection, testProbe, clientFactory, mockConnectionActor.ref(),
                dittoHeaders, ConfigFactory.empty());
    }

    @Override
    protected Mqtt5Publish mqttMessage(final String topic, final String payload) {
        return Mqtt5Publish.builder()
                .topic(topic)
                .payload(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    protected String extractPayload(final Mqtt5Publish message) {
        return message.getPayload().map(ByteBufferUtils::toUtf8String).orElse(null);
    }

    @Override
    protected String extractTopic(final Mqtt5Publish message) {
        return message.getTopic().toString();
    }

    @Override
    protected Class<Mqtt5Publish> getMessageClass() {
        return Mqtt5Publish.class;
    }

}
