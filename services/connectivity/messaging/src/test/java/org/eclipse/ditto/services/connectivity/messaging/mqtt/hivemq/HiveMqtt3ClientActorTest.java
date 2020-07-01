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

import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

public class HiveMqtt3ClientActorTest extends AbstractMqttClientActorTest<Mqtt3Publish> {

    private MockHiveMqtt3ClientFactory mockHiveMqtt3ClientFactory;

    @Before
    public void initClient() {
        // init Mqtt3Client in before because this takes several minutes and causes test timeouts if done on demand
        Mockito.mock(Mqtt3Client.class);
        mockHiveMqtt3ClientFactory = new MockHiveMqtt3ClientFactory();
    }

    @Override
    protected void expectDisconnectCalled() {
        mockHiveMqtt3ClientFactory.expectDisconnectCalled();
    }

    @Test
    public void testSubscribeFails() {
        new TestKit(actorSystem) {{
            final MockHiveMqtt3ClientFactory clientFactory = mockHiveMqtt3ClientFactory
                    .withTestProbe(getRef())
                    .withFailingSubscribe();

            final Props props = HiveMqtt3ClientActor.props(connection, getRef(), getRef(), clientFactory);
            final ActorRef mqttClientActor = actorSystem.actorOf(props, "mqttClientActor-testSubscribeFails");

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return HiveMqtt3ClientActor.props(connection, proxyActor, proxyActor,
                mockHiveMqtt3ClientFactory.withTestProbe(proxyActor));
    }

    @Override
    protected Props createFailingClientActor(final ActorRef testProbe) {
        return HiveMqtt3ClientActor.props(connection, testProbe, testProbe,
                mockHiveMqtt3ClientFactory
                        .withException(new RuntimeException("failed to connect")));
    }

    @Override
    protected Props createClientActorWithMessages(final Connection connection,
            final ActorRef testProbe,
            final List<Mqtt3Publish> messages) {
        final MockHiveMqtt3ClientFactory clientFactory = mockHiveMqtt3ClientFactory
                .withMessages(messages)
                .withTestProbe(testProbe);
        return HiveMqtt3ClientActor.props(connection, testProbe, testProbe, clientFactory);
    }

    @Override
    protected Mqtt3Publish mqttMessage(final String topic, final String payload) {
        return Mqtt3Publish.builder()
                .topic(topic)
                .payload(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    protected String extractPayload(final Mqtt3Publish message) {
        return message.getPayload().map(ByteBufferUtils::toUtf8String).orElse(null);
    }

    @Override
    protected String extractTopic(final Mqtt3Publish message) {
        return message.getTopic().toString();
    }

    @Override
    protected Class<Mqtt3Publish> getMessageClass() {
        return Mqtt3Publish.class;
    }

}