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
package org.eclipse.ditto.connectivity.service.messaging.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.Session;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsSession;
import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.WithMockServers;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ConnectionFailure;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests the {@link JMSConnectionHandlingActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public class JmsConnectionHandlingActorTest extends WithMockServers {

    private static ActorSystem actorSystem;

    private static final ConnectionId connectionId = TestConstants.createRandomConnectionId();
    private static Connection connection;

    @Mock private final Session mockSession = mock(Session.class);
    @Mock private final JmsConnection mockConnection = mock(JmsConnection.class);
    @Mock private final ConnectionLogger connectionLogger = mock(ConnectionLogger.class);
    private final JmsConnectionFactory jmsConnectionFactory = (c, e, l, i) -> mockConnection;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(connectionId);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @Before
    public void init() throws JMSException {
        when(mockConnection.createSession(anyInt())).thenReturn(mockSession);
    }

    @Test
    public void handleFailureOnJmsConnect() throws JMSException {
        new TestKit(actorSystem) {{

            final IllegalStateException exception = new IllegalStateException("failureOnJmsConnect");
            doThrow(exception).when(mockConnection).start();

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);
            final String clientId = UUID.randomUUID().toString();
            connectionHandlingActor.tell(new AmqpClientActor.JmsConnect(origin.ref(), clientId), getRef());

            final ConnectionFailure connectionFailure1 = expectMsgClass(ConnectionFailure.class);
            assertThat(connectionFailure1.getOrigin()).contains(origin.ref());
            assertThat(connectionFailure1.getFailure().cause()).isSameAs(exception);
        }};
    }

    @Test
    public void handleFailureWhenCreatingConsumers() throws JMSException {
        new TestKit(actorSystem) {{

            final IllegalStateException exception = new IllegalStateException("failureOnCreateConsumer");
            doThrow(exception).when(mockSession).createConsumer(any());

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);
            final String clientId = UUID.randomUUID().toString();
            connectionHandlingActor.tell(new AmqpClientActor.JmsConnect(origin.ref(), clientId), getRef());

            final ConnectionFailure connectionFailure1 = expectMsgClass(ConnectionFailure.class);
            assertThat(connectionFailure1.getOrigin()).contains(origin.ref());
            assertThat(connectionFailure1.getFailure().cause()).isSameAs(exception);
        }};
    }

    @Test
    public void handleJmsConnect() throws JMSException {
        new TestKit(actorSystem) {{

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);
            final String clientId = UUID.randomUUID().toString();
            connectionHandlingActor.tell(new AmqpClientActor.JmsConnect(origin.ref(), clientId), getRef());

            final ClientConnected connected = expectMsgClass(ClientConnected.class);
            assertThat(connected.getOrigin()).contains(origin.ref());

            verify(mockConnection).start();
            verify(mockSession, times(connection.getSources()
                    .stream()
                    .mapToInt(s -> s.getAddresses().size() * s.getConsumerCount())
                    .sum())).createConsumer(any());
        }};
    }


    @Test
    public void handleRecoverSession() throws JMSException {
        new TestKit(actorSystem) {{

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);

            final JmsSession existingSession = mock(JmsSession.class);
            connectionHandlingActor.tell(
                    new AmqpClientActor.JmsRecoverSession(origin.ref(), mockConnection, existingSession),
                    getRef());

            final AmqpClientActor.JmsSessionRecovered recovered =
                    expectMsgClass(AmqpClientActor.JmsSessionRecovered.class);
            Assertions.assertThat(recovered.getOrigin()).contains(origin.ref());
            assertThat(recovered.getSession()).isSameAs(mockSession);

            verify(existingSession).close();
            verify(mockConnection).createSession(anyInt());
            verify(mockSession, times(connection.getSources()
                    .stream()
                    .mapToInt(s -> s.getAddresses().size() * s.getConsumerCount())
                    .sum())).createConsumer(any());
        }};
    }


    @Test
    public void handleRecoverSessionFails() throws JMSException {
        new TestKit(actorSystem) {{

            final JmsConnection failsToCreateSession = mock(JmsConnection.class);
            when(failsToCreateSession.createSession(anyInt())).thenThrow(new JMSException("failed to create session"));

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));
            connectionHandlingActor.tell(
                    new AmqpClientActor.JmsRecoverSession(getRef(), failsToCreateSession, mockSession),
                    getRef());

            expectMsgClass(ConnectionFailure.class);
            verify(mockSession).close();
            verify(failsToCreateSession).createSession(anyInt());
        }};
    }

    @Test
    public void handleJmsDisconnect() throws JMSException {
        new TestKit(actorSystem) {{

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);
            connectionHandlingActor.tell(new AmqpClientActor.JmsDisconnect(origin.ref(), mockConnection,
                    false), getRef());

            final ClientDisconnected disconnected = expectMsgClass(ClientDisconnected.class);
            assertThat(disconnected.getOrigin()).contains(origin.ref());

            verify(mockConnection).close();
        }};
    }

    @Test
    public void handleFailureDuringJmsDisconnect() throws JMSException {
        new TestKit(actorSystem) {{

            final JMSException exception = new JMSException("failureOnJmsConnect");
            doThrow(exception).when(mockConnection).stop();

            final Props props =
                    JMSConnectionHandlingActor.props(connection, e -> {}, jmsConnectionFactory, connectionLogger);
            final ActorRef connectionHandlingActor = watch(actorSystem.actorOf(props));

            final TestProbe origin = TestProbe.apply(actorSystem);
            connectionHandlingActor.tell(new AmqpClientActor.JmsDisconnect(origin.ref(), mockConnection,
                    false), getRef());

            final ClientDisconnected disconnected = expectMsgClass(ClientDisconnected.class);
            assertThat(disconnected.getOrigin()).contains(origin.ref());

            verify(mockConnection).close();
        }};
    }
}
