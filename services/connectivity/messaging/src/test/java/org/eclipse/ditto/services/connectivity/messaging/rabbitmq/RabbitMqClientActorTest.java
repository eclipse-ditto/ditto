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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionStatus;
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public class RabbitMqClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);

    private static final IllegalArgumentException CUSTOM_EXCEPTION = new IllegalArgumentException("rabbitmq");

    @SuppressWarnings("NullableProblems") private static ActorSystem actorSystem;

    private final String connectionId = TestConstants.createRandomConnectionId();
    private final Connection connection = TestConstants.createConnection(connectionId);
    private final ConnectionStatus connectionStatus = ConnectionStatus.OPEN;

    @Mock
    private ConnectionFactory mockConnectionFactory = Mockito.mock(ConnectionFactory.class);
    @Mock
    private com.rabbitmq.client.Connection mockConnection = Mockito.mock(com.rabbitmq.client.Connection.class);
    @Mock
    private Channel mockChannel = Mockito.mock(Channel.class);

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
    public void testExceptionDuringConnectionFactoryCreation() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props = RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                    (con, exHandler) -> { throw CUSTOM_EXCEPTION; });
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(CUSTOM_EXCEPTION));
        }};
    }

    @Test
    @Ignore("TODO TJ fix")
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props = RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                    (con, exHandler) -> mockConnectionFactory);
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
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                    (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);
            watch(rabbitClientActor);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    @Ignore("TODO TJ fix")
    public void sendConnectCommandWhenAlreadyConnected() throws IOException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                            (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(OpenConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);
            verify(mockConnection, Mockito.times(1)).createChannel();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                            (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CloseConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    @Ignore("TODO TJ fix")
    public void testStartConnectionFails() throws IOException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            doThrow(CUSTOM_EXCEPTION).when(mockConnection).createChannel();
            final Props props =
                    RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                            (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(CUSTOM_EXCEPTION));
        }};
    }

    @Test
    @Ignore("TODO TJ fix")
    public void testCloseConnectionFails() throws IOException {
        new TestKit(actorSystem) {{
            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            doThrow(CUSTOM_EXCEPTION).when(mockConnection).close();
            final Props props =
                    RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                            (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            rabbitClientActor.tell(DeleteConnection.of(connectionId, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    @Ignore("TODO TJ fix")
    public void testConsumeMessageAndExpectForwardToProxyActor() throws IOException {
        new TestKit(actorSystem) {{
            final ActorRef mediator = DistributedPubSub.get(actorSystem).mediator();
            mediator.tell(new DistributedPubSubMediator.Put(getRef()), getRef());

            final String pubSubTargetPath = getRef().path().toStringWithoutAddress();
            final Props props =
                    RabbitMQClientActor.props(connection, connectionStatus, pubSubTargetPath,
                            (con, exHandler) -> mockConnectionFactory);
            final ActorRef rabbitClientActor = actorSystem.actorOf(props);

            rabbitClientActor.tell(CreateConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
            verify(mockChannel, timeout(1000).atLeastOnce()).setDefaultConsumer(captor.capture());
            final Consumer consumer = captor.getValue();
            consumer.handleDelivery("foo", Mockito.mock(Envelope.class),
                    Mockito.mock(AMQP.BasicProperties.class), new byte[]{});

            final Command command = expectMsgClass(Command.class);
            assertThat(command.getId()).isEqualTo(TestConstants.THING_ID);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
        }};
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
