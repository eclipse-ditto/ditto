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
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.services.connectivity.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
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

import akka.actor.Actor;
import akka.actor.ActorRef;
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
    private static final ConnectivityStatus CONNECTION_STATUS = ConnectivityStatus.OPEN;

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;
    private static Connection connection;

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
        connection = TestConstants.createConnection(CONNECTION_ID)
                .toBuilder()
                .connectionStatus(ConnectivityStatus.CLOSED)
                .build();
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS), false);
    }

    @Before
    public void init() throws IOException, TimeoutException {
        when(mockConnectionFactory.newConnection()).thenReturn(mockConnection);
        when(mockConnection.createChannel()).thenReturn(mockChannel);
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
                            getRef(), (con, exHandler) -> { throw CUSTOM_EXCEPTION; })
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
    public void testConnectionWithoutPublisherHandling() {
        new TestKit(actorSystem) {{
            final ConnectionId randomConnectionId = TestConstants.createRandomConnectionId();
            final Connection connectionWithoutTargets =
                    TestConstants.createConnection(randomConnectionId, new Target[0]);
            final Props props =
                    RabbitMQClientActor.propsForTests(connectionWithoutTargets, getRef(), getRef(),
                            (con, exHandler) -> mockConnectionFactory).withDispatcher(CallingThreadDispatcher.Id());
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
            verify(mockConnection, Mockito.times(2)).createChannel();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props = createClientActor(getRef(), getConnection(false));
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
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
    protected Props createClientActor(final ActorRef testProbe, final Connection connection) {
        return RabbitMQClientActor.propsForTests(connection, testProbe,
                testProbe, (con, exHandler) -> mockConnectionFactory)
                .withDispatcher(CallingThreadDispatcher.Id());
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

}
