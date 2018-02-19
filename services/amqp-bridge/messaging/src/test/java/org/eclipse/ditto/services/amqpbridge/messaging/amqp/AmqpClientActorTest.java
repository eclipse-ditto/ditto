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

import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createConnection;
import static org.eclipse.ditto.services.amqpbridge.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class AmqpClientActorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpClientActorTest.class);

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(AmqpClientActor.State.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS =
            new Status.Success(AmqpClientActor.State.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");

    private static ActorSystem actorSystem;

    private final String connectionId = createRandomConnectionId();
    private final AmqpConnection amqpConnection = createConnection(connectionId);

    @Mock
    private Connection mockConnection = Mockito.mock(Connection.class);
    @Mock
    private Session mockSession = Mockito.mock(Session.class);

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                    false);
        }
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(),
                    (ac, el) -> { throw JMS_EXCEPTION; });
            final ActorRef amqpConnectionActor = actorSystem.actorOf(props);

            amqpConnectionActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() throws JMSException {
        new TestKit(actorSystem) {{

            when(mockConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(mockSession);

            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection, null,
                    (amqpConnection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            LOGGER.info("----------> CREATE");
            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            LOGGER.info("----------> CLOSE");
            amqpClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            LOGGER.info("----------> DELETE");
            amqpClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(),
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
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(), (ac, el) -> mockConnection);
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
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(), (ac, el) -> mockConnection);
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
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(false, Session.AUTO_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testConsumeFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(JMS_EXCEPTION));
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, null, (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CreateConnection.of(amqpConnection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testInitAfterTimeout() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.props(connectionId, getRef(), amqpConnection, null, (ac, el) -> mockConnection);
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

    private <T> T waitForLatchAndReturn(final CountDownLatch latch, T result) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

}