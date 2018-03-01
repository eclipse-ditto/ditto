/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.services.amqpbridge.messaging.amqp;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createConnection;
import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsQueue;
import org.eclipse.ditto.model.amqpbridge.AmqpConnection;
import org.eclipse.ditto.model.amqpbridge.ConnectionStatus;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.amqpbridge.messaging.TestConstants;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.CreateConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.DeleteConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnection;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionResponse;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.amqpbridge.query.RetrieveConnectionStatusResponse;
import org.eclipse.ditto.signals.commands.base.Command;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class AmqpClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpClientActorTest.class);

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(AmqpClientActor.State.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS =
            new Status.Success(AmqpClientActor.State.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;

    private final String connectionId = createRandomConnectionId();
    private final AmqpConnection amqpConnection = createConnection(connectionId);

    @Mock
    private Connection mockConnection = Mockito.mock(Connection.class);
    @Mock
    private Session mockSession = Mockito.mock(Session.class);
    @Mock
    private MessageConsumer mockConsumer = Mockito.mock(MessageConsumer.class);
    @Mock
    private MessageProducer mockProducer = Mockito.mock(MessageProducer.class);
    @Mock
    private TextMessage mockTextMessage = Mockito.mock(TextMessage.class);

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
    public void init() throws JMSException {
        when(mockConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(mockSession);
        when(mockSession.createConsumer(any(JmsQueue.class))).thenReturn(mockConsumer);
        when(mockSession.createProducer(any(Destination.class))).thenReturn(mockProducer);
        when(mockSession.createTextMessage(anyString())).thenReturn(mockTextMessage);
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection,
                    pubSubTargetPath,
                    (ac, el) -> { throw JMS_EXCEPTION; });
            final ActorRef amqpConnectionActor = actorSystem.actorOf(props);

            amqpConnectionActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                    (amqpConnection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
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
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                    (ac, el) -> waitForLatchAndReturn(latch, mockConnection));
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);
            Mockito.verify(mockConnection, Mockito.times(1)).start();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            when(mockConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testInitializeClientActorAfterTimeout() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            actorSystem.actorOf(props);

            // expect retrieve commands after configured timeout
            expectMsg(RetrieveConnection.of(connectionId, DittoHeaders.empty()));
            expectMsg(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()));

            getLastSender().tell(RetrieveConnectionResponse.of(amqpConnection, DittoHeaders.empty()), getRef());
            getLastSender().tell(
                    RetrieveConnectionStatusResponse.of(connectionId, ConnectionStatus.OPEN, DittoHeaders.empty()),
                    getRef());

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActor() throws JMSException {
        new TestKit(actorSystem) {{
            final ActorRef mediator = DistributedPubSub.get(actorSystem).mediator();
            mediator.tell(new DistributedPubSubMediator.Put(getRef()), getRef());

            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
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
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, pubSubTargetPath,
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(TestConstants.thingModified(singletonList("")), getRef());

            verify(mockProducer, timeout(2000)).send(mockTextMessage);
        }};
    }

    private Message mockMessage() throws JMSException {
        final TextMessage message = Mockito.mock(TextMessage.class);
        when(message.getJMSCorrelationID()).thenReturn("cid");
        when(message.getJMSReplyTo()).thenReturn(new JmsQueue("reply"));
        when(message.getPropertyNames()).thenReturn(Collections.enumeration(Collections.singletonList("header1")));
        when(message.getObjectProperty(anyString())).thenReturn("value");
        when(message.getText()).thenReturn(TestConstants.modifyThing());
        return message;
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