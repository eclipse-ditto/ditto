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

package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.assertj.core.api.ThrowableAssert;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class AmqpClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;

    private static final String connectionId = TestConstants.createRandomConnectionId();
    private static Connection connection;
    private final ConnectionStatus connectionStatus = ConnectionStatus.OPEN;

    @Mock
    private final JmsConnection mockConnection = Mockito.mock(JmsConnection.class);
    private final JmsConnectionFactory jmsConnectionFactory = (connection1, exceptionListener) -> mockConnection;
    @Mock
    private final Session mockSession = Mockito.mock(Session.class);
    @Mock
    private final MessageConsumer mockConsumer = Mockito.mock(MessageConsumer.class);
    @Mock
    private final MessageProducer mockProducer = Mockito.mock(MessageProducer.class);
    @Mock
    private final TextMessage mockTextMessage = Mockito.mock(TextMessage.class);

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(connectionId, actorSystem);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @Before
    public void init() throws JMSException {
        when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
        when(mockSession.createConsumer(any(JmsQueue.class))).thenReturn(mockConsumer);
        when(mockSession.createProducer(any(Destination.class))).thenReturn(mockProducer);
        when(mockSession.createTextMessage(anyString())).thenReturn(mockTextMessage);
    }

    @Test
    public void invalidSpecificOptionsThrowConnectionConfigurationInvalidException() {
        final HashMap<String, String> specificOptions = new HashMap<>();
        specificOptions.put("failover.unknown.option", "100");
        specificOptions.put("failover.nested.amqp.vhost", "ditto");
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(createRandomConnectionId(),
                ConnectionType.AMQP_10, ConnectionStatus.OPEN, TestConstants.getUri(actorSystem), TestConstants.AUTHORIZATION_CONTEXT)
                .specificConfig(specificOptions)
                .sources(Collections.singleton(ConnectivityModelFactory.newSource(1, "source1")))
                .build();

        final ThrowableAssert.ThrowingCallable props1 = () -> AmqpClientActor.propsForTests(connection, connectionStatus, null, null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> AmqpClientActor.propsForTests(connection, connectionStatus, null, jmsConnectionFactory);

        Stream.of(props1, props2).forEach(throwingCallable ->
                assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                        .isThrownBy(throwingCallable)
                        .withMessageContaining("unknown.option"));
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (ac, el) -> { throw JMS_EXCEPTION; });
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            amqpClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = AmqpClientActor.propsForTests(connection, connectionStatus, getRef(),
                    (ac, el) -> waitForLatchAndReturn(latch, mockConnection));
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // trigger a reconnect:
            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);
            Mockito.verify(mockConnection, Mockito.times(2)).start();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(Session.CLIENT_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActor() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();
            messageListener.onMessage(mockMessage());

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getId()).isEqualTo(TestConstants.THING_ID);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
        }};
    }

    @Test
    public void testReceiveThingEventAndExpectForwardToJMSProducer() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, connectionStatus, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(TestConstants.thingModified(singletonList("")), getRef());

            verify(mockProducer, timeout(2000)).send(mockTextMessage);
        }};
    }

    private Message mockMessage() throws JMSException {
        final AmqpJmsTextMessageFacade amqpJmsTextMessageFacade = new AmqpJmsTextMessageFacade();
        amqpJmsTextMessageFacade.setContentType(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE);
        amqpJmsTextMessageFacade.initialize(Mockito.mock(AmqpConnection.class));

        final JmsTextMessage jmsTextMessage = new JmsTextMessage(amqpJmsTextMessageFacade);
        jmsTextMessage.setJMSCorrelationID("cid");
        jmsTextMessage.setJMSReplyTo(new JmsQueue("reply"));
        jmsTextMessage.setText(TestConstants.modifyThing());
        return jmsTextMessage;
    }

    private <T> T waitForLatchAndReturn(final CountDownLatch latch, final T result) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

}
