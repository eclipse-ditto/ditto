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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.hivemq.client.internal.checkpoint.Confirmable;
import com.hivemq.client.internal.mqtt.message.publish.mqtt3.Mqtt3PublishView;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class HiveMqtt3ClientActorTest extends AbstractMqttClientActorTest<Mqtt3Publish> {

    private static final TestConstants.FreePort FREE_PORT = new TestConstants.FreePort();

    @ClassRule
    public static final MqttServerRule MQTT_SERVER = new MqttServerRule(FREE_PORT.getPort());

    private MockHiveMqtt3ClientFactory mockHiveMqtt3ClientFactory;

    @Before
    public void initClient() {
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

            final Props props = HiveMqtt3ClientActor.props(connection, getRef(), getRef(), clientFactory, dittoHeaders,
                    ConfigFactory.empty());
            final ActorRef mqttClientActor = actorSystem.actorOf(props, "mqttClientActor-testSubscribeFails");

            mqttClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsgClass(Duration.ofSeconds(20L), Status.Failure.class);

            mqttClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return HiveMqtt3ClientActor.props(connection, proxyActor, proxyActor,
                mockHiveMqtt3ClientFactory.withTestProbe(proxyActor), dittoHeaders, ConfigFactory.empty());
    }

    @Override
    protected TestConstants.FreePort getFreePort() {
        return FREE_PORT;
    }

    @Override
    protected Props createFailingClientActor(final ActorRef testProbe) {
        return HiveMqtt3ClientActor.props(connection, testProbe, testProbe,
                mockHiveMqtt3ClientFactory
                        .withException(new RuntimeException("failed to connect")), dittoHeaders, ConfigFactory.empty());
    }

    @Override
    protected Props createClientActorWithMessages(final Connection connection,
            final ActorRef testProbe,
            final List<Mqtt3Publish> messages) {
        final MockHiveMqtt3ClientFactory clientFactory = mockHiveMqtt3ClientFactory
                .withMessages(messages)
                .withTestProbe(testProbe);
        return HiveMqtt3ClientActor.props(connection, testProbe, testProbe, clientFactory, dittoHeaders,
                ConfigFactory.empty());
    }

    @Override
    protected Mqtt3Publish mqttMessage(final String topic, final String payload) {
        final Mqtt3PublishView p = (Mqtt3PublishView) Mqtt3Publish.builder()
                .topic(topic)
                .payload(ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8)))
                .build();
        return Mqtt3PublishView.of(p.getDelegate().withConfirmable(new Confirmable() {
            @Override
            public long getId() {
                return 0;
            }

            @Override
            public boolean confirm() {
                return true;
            }
        }));
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
