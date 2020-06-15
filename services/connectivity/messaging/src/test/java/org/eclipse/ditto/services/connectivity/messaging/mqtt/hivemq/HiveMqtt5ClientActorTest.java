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
package org.eclipse.ditto.services.connectivity.messaging.mqtt.hivemq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.ditto.model.base.common.ByteBufferUtils;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.connectivity.messaging.mqtt.AbstractMqttClientActorTest;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

public class HiveMqtt5ClientActorTest extends AbstractMqttClientActorTest<Mqtt5Publish> {

    private MockHiveMqtt5ClientFactory mockHiveMqtt5ClientFactory;

    @Before
    public void initClient() {
        // init Mqtt5Client in before because this takes several minutes and causes test timeouts if done on demand
        Mockito.mock(Mqtt5Client.class);
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
                    HiveMqtt5ClientActor.props(connection, getRef(), clientFactory, mockConnectionActor.ref());
            final ActorRef mqttClientActor = actorSystem.actorOf(props, "mqttClientActor-testSubscribeFails");

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Override
    protected Props createClientActor(final ActorRef testProbe, final Connection connection) {
        return HiveMqtt5ClientActor.props(connection, testProbe,
                mockHiveMqtt5ClientFactory.withTestProbe(testProbe), mockConnectionActor.ref());
    }

    @Override
    protected Props createFailingClientActor(final ActorRef testProbe) {
        return HiveMqtt5ClientActor.props(connection, testProbe,
                mockHiveMqtt5ClientFactory
                        .withException(new RuntimeException("failed to connect")), mockConnectionActor.ref());
    }

    @Override
    protected Props createClientActorWithMessages(final Connection connection,
            final ActorRef testProbe,
            final List<Mqtt5Publish> messages) {
        final MockHiveMqtt5ClientFactory clientFactory = mockHiveMqtt5ClientFactory
                .withMessages(messages)
                .withTestProbe(testProbe);
        return HiveMqtt5ClientActor.props(connection, testProbe, clientFactory, mockConnectionActor.ref());
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