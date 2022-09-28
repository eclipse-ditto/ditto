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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.MODIFY_THING_WITH_ACK;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Sources.AMQP_SOURCE_ADDRESS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.newmotion.akka.rabbitmq.AmqpShutdownSignal;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.CallingThreadDispatcher;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public final class RabbitMQClientActorTest extends AbstractBaseClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);

    private static final IllegalArgumentException CUSTOM_EXCEPTION =
            new IllegalArgumentException("custom error message");

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    private ActorSystem actorSystem;
    private Connection connection;

    @Mock private ConnectionFactory mockConnectionFactory;
    @Mock private ConnectionFactory failingMockConnectionFactory;
    @Mock private com.rabbitmq.client.Connection mockConnection;
    @Mock private com.rabbitmq.client.Connection mockReconnection;
    @Mock private Channel mockChannel;
    @Mock private Channel mockChannelReconnected;

    private final RabbitConnectionFactoryFactory rabbitConnectionFactoryFactory =
            (con, exHandler, connectionLogger) -> mockConnectionFactory;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(CONNECTION_ID)
                .toBuilder()
                .connectionStatus(ConnectivityStatus.CLOSED)
                .build();
    }

    @After
    public void tearDown() {
        actorSystem.terminate();
    }

    @Before
    public void init() throws IOException, TimeoutException {
        when(mockConnectionFactory.newConnection()).thenReturn(mockConnection);
        when(failingMockConnectionFactory.newConnection())
                .thenReturn(mockConnection)
                .thenReturn(mockReconnection);
        when(mockConnection.createChannel()).thenReturn(mockChannel);
        when(mockReconnection.createChannel()).thenReturn(mockChannelReconnected);
    }

    @Test
    public void invalidTargetFormatThrowsConnectionConfigurationInvalidException() {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.AMQP_091, ConnectivityStatus.OPEN, TestConstants.getUriOfNewMockServer())
                .targets(Collections.singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address("exchangeOnly")
                        .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                        .topics(Topic.TWIN_EVENTS)
                        .build()))
                .build();

        final ThrowableAssert.ThrowingCallable props1 =
                () -> RabbitMQClientActor.propsForTests(connection, Actor.noSender(), Actor.noSender(), null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> RabbitMQClientActor.propsForTests(connection, Actor.noSender(), Actor.noSender(),
                        rabbitConnectionFactoryFactory
                );
        Stream.of(props1, props2)
                .forEach(throwingCallable ->
                        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                                .isThrownBy(throwingCallable)
                                .withMessageContaining("exchangeOnly")
                                .withNoCause()
                );
    }

    @Test
    public void testExceptionDuringConnectionFactoryCreation() {
        new TestKit(actorSystem) {{
            final Props props =
                    RabbitMQClientActor.propsForTests(connection, getRef(),
                                    getRef(), (con, exHandler, connectionLogger) -> {throw CUSTOM_EXCEPTION;})
                            .withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testReconnectionHandling() throws IOException {
        new TestKit(actorSystem) {{
            final ArgumentCaptor<Consumer> consumer = ArgumentCaptor.forClass(Consumer.class);
            final ArgumentCaptor<Consumer> reconnectedConsumer = ArgumentCaptor.forClass(Consumer.class);

            final Connection connection = getConnection(false);
            final Props props = getClientActorProps(getRef(), connection, failingMockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // 1 consume channel + 1 publish channel
            verify(mockConnection, times(2)).createChannel();
            // 2 invocations because consumer count is set to 2
            verify(mockChannel, times(2)).basicConsume(eq(AMQP_SOURCE_ADDRESS), eq(false),
                    consumer.capture());

            // verify inbound signal processed
            sendInboundModifyThingProtocolMessage(consumer.getValue());
            final ModifyThing modifyThing = expectMsgClass(ModifyThing.class);

            // verify outbound signal processed
            final Signal<?> thingModified = TestConstants.thingModified(
                    TestConstants.Targets.TWIN_TARGET.getAuthorizationContext().getAuthorizationSubjects());

            rabbitClientActor.tell(thingModified, ActorRef.noSender());
            verifyPublishOnChannel(mockChannel);

            // simulate rabbitmq connection gets interrupted
            final AmqpShutdownSignal shutdownSignal =
                    AmqpShutdownSignal.apply(new ShutdownSignalException(true, true, null, mockConnection));
            ActorSelection.apply(rabbitClientActor, "rmq-connection*").tell(shutdownSignal, getRef());

            // verify consume and publish channel created on new connection
            verify(mockReconnection, timeout(6_000).times(2)).createChannel();
            // verify basicConsume called on new channel
            verify(mockChannelReconnected, timeout(6_000).times(2))
                    .basicConsume(eq(AMQP_SOURCE_ADDRESS), eq(false), reconnectedConsumer.capture());

            // verify inbound signal processed after reconnect
            sendInboundModifyThingProtocolMessage(reconnectedConsumer.getValue());
            expectMsgClass(ModifyThing.class);

            // verify outbound signal processed after reconnect
            rabbitClientActor.tell(thingModified, ActorRef.noSender());
            verifyPublishOnChannel(mockChannelReconnected);

            rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    private void verifyPublishOnChannel(final Channel ch) throws IOException {
        verify(ch, timeout(500).times(1))
                .basicPublish(eq("twinEventExchange"), eq("twinEventRoutingKey"), eq(true),
                        any(AMQP.BasicProperties.class), any(byte[].class));
    }

    private void sendInboundModifyThingProtocolMessage(final Consumer consumer) throws IOException {
        final Envelope envelope = new Envelope(1L, false, "exchange", "key");
        final AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder().build();
        consumer.handleDelivery("whatever", envelope, basicProperties, MODIFY_THING_WITH_ACK.getBytes());
    }

    @Test
    public void testConnectionWithoutPublisherHandling() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connectionWithoutTargets =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props =
                    RabbitMQClientActor.propsForTests(connectionWithoutTargets, getRef(), getRef(),
                                    (con, exHandler, connectionLogger) -> mockConnectionFactory)
                            .withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(OpenConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(CloseConnection.of(randomConnectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testReconnection() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            // reconnect a few times
            for (int i = 0; i < 3; ++i) {
                rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);

                rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(DISCONNECTED_SUCCESS);
            }
        }};
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws IOException {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // a publisher and a consumer channel should be created
            verify(mockConnection, Mockito.timeout(500).times(2)).createChannel();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyNoInteractions(mockConnection);
        }};
    }

    @Test
    public void testCloseConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        return isSecure ? setScheme(connection, "amqps") : connection;
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return getClientActorProps(proxyActor, connection, mockConnectionFactory);
    }

    private Props getClientActorProps(final ActorRef proxyActor, final Connection connection,
            final ConnectionFactory connectionFactory) {
        return RabbitMQClientActor.propsForTests(connection, proxyActor, proxyActor,
                (con, exHandler, connectionLogger) -> connectionFactory).withDispatcher(CallingThreadDispatcher.Id());
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

}
