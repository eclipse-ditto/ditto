/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.services.connectivity.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.CallingThreadDispatcher;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class RabbitMQClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);

    private static final IllegalArgumentException CUSTOM_EXCEPTION = new IllegalArgumentException("rabbitmq");

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;

    private final String connectionId = TestConstants.createRandomConnectionId();
    private final Connection connection = TestConstants.createConnection(connectionId);
    private final ConnectionStatus connectionStatus = ConnectionStatus.OPEN;

    @Mock
    private final ConnectionFactory mockConnectionFactory = Mockito.mock(ConnectionFactory.class);
    private final RabbitConnectionFactoryFactory
            rabbitConnectionFactoryFactory = (con, exHandler) -> mockConnectionFactory;
    @Mock
    private final com.rabbitmq.client.Connection mockConnection = Mockito.mock(com.rabbitmq.client.Connection.class);
    @Mock
    private final Channel mockChannel = Mockito.mock(Channel.class);

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @Before
    public void init() throws IOException, TimeoutException {
        when(mockConnectionFactory.newConnection()).thenReturn(mockConnection);
        when(mockConnection.createChannel()).thenReturn(mockChannel);
    }

    @Test
    public void invalidTargetFormatThrowsConnectionConfigurationInvalidException() {
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder("ditto", ConnectionType.AMQP_091,
                ConnectionStatus.OPEN, TestConstants.URI, TestConstants.AUTHORIZATION_CONTEXT)
                .targets(Collections.singleton(ConnectivityModelFactory.newTarget("exchangeOnly", "topic1")))
                .build();

        final ThrowableAssert.ThrowingCallable props1 =
                () -> RabbitMQClientActor.propsForTests(connection, connectionStatus, null, null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> RabbitMQClientActor.propsForTests(connection, connectionStatus, null, rabbitConnectionFactoryFactory);
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
            final Props props = RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (con, exHandler) -> { throw CUSTOM_EXCEPTION; }).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(CUSTOM_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final Props props = RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            rabbitClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final Props props = RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws IOException {
        new TestKit(actorSystem) {{
            final Props props =
                    RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                            (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);
            verify(mockConnection, Mockito.atLeast(2)).createChannel();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props =
                    RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                            (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testCloseConnectionFails() {
        new TestKit(actorSystem) {{
            final Props props =
                    RabbitMQClientActor.propsForTests(connection, connectionStatus, getRef(),
                            (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

}
