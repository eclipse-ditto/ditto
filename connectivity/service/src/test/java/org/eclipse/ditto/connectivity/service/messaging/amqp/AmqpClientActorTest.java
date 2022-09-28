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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.jms.CompletionListener;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.JmsMessageConsumer;
import org.apache.qpid.jms.JmsQueue;
import org.apache.qpid.jms.JmsSession;
import org.apache.qpid.jms.message.JmsMessage;
import org.apache.qpid.jms.message.JmsTextMessage;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.message.AmqpJmsTextMessageFacade;
import org.apache.qpid.jms.provider.exceptions.ProviderSecurityException;
import org.apache.qpid.proton.amqp.Symbol;
import org.assertj.core.api.ThrowableAssert;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.SignalWithEntityId;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.ResourceStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.exceptions.ConnectionFailedException;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.CloseConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.model.signals.commands.query.RetrieveConnectionStatus;
import org.eclipse.ditto.connectivity.service.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization;
import org.eclipse.ditto.internal.utils.pubsubthings.DittoProtocolSub;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingModifiedEvent;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.FSM;
import akka.actor.Props;
import akka.actor.Status;
import akka.pattern.AskTimeoutException;
import akka.testkit.TestActorRef;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

// Silencing "unnecessary stubbing" - it happens only on Travis?
@RunWith(MockitoJUnitRunner.Silent.class)
public final class AmqpClientActorTest extends AbstractBaseClientActorTest {

    private static final Status.Success CONNECTED_SUCCESS = new Status.Success(BaseClientState.CONNECTED);
    private static final Status.Success DISCONNECTED_SUCCESS = new Status.Success(BaseClientState.DISCONNECTED);
    private static final JMSException JMS_EXCEPTION = new JMSException("FAIL");
    private static final URI DUMMY = URI.create("amqp://test:1234");
    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();

    private ActorSystem actorSystem;
    private Connection connection;

    @Mock
    private final JmsConnection mockConnection = Mockito.mock(JmsConnection.class);
    private final JmsConnectionFactory jmsConnectionFactory =
            (c, e, l, i) -> mockConnection;
    @Mock
    private final JmsSession mockSession = Mockito.mock(JmsSession.class);
    @Mock
    private final JmsMessageConsumer mockConsumer = Mockito.mock(JmsMessageConsumer.class);

    private final List<MessageProducer> mockProducers = new LinkedList<>();

    private ArgumentCaptor<JmsConnectionListener> listenerArgumentCaptor;
    private TestProbe connectionActorProbe;
    private ActorRef connectionActor;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(CONNECTION_ID)
                .toBuilder()
                .connectionStatus(ConnectivityStatus.CLOSED)
                .build();
        // instantiate the pubsub extension now to prevent flapping tests due to the delay
        DittoProtocolSub.get(actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @AfterClass
    public static void stopMockServers() {
        TestConstants.stopMockServers();
    }

    @Before
    public void init() throws JMSException {
        Mockito.reset(mockConnection, mockSession, mockConsumer);
        when(mockConnection.createSession(anyInt())).thenReturn(mockSession);
        listenerArgumentCaptor = ArgumentCaptor.forClass(JmsConnectionListener.class);
        doNothing().when(mockConnection).addConnectionListener(listenerArgumentCaptor.capture());
        prepareSession(mockSession, mockConsumer);
        connectionActorProbe = TestProbe.apply("connectionActor", actorSystem);
        connectionActor = connectionActorProbe.ref();
    }

    private void prepareSession(final Session mockSession, final JmsMessageConsumer mockConsumer) throws JMSException {
        doReturn(mockConsumer).when(mockSession).createConsumer(any(JmsQueue.class));
        doAnswer((Answer<MessageProducer>) destinationInv -> {
            final MessageProducer messageProducer = mock(MessageProducer.class);
            doReturn(destinationInv.getArgument(0)).when(messageProducer).getDestination();
            mockProducers.add(messageProducer);
            return messageProducer;
        }).when(mockSession).createProducer(any(Destination.class));
        doAnswer((Answer<JmsMessage>) textMsgInv -> {
            final String textMsg = textMsgInv.getArgument(0);
            final AmqpJmsTextMessageFacade facade = new AmqpJmsTextMessageFacade();
            facade.initialize(Mockito.mock(AmqpConnection.class));
            final JmsTextMessage jmsTextMessage = new JmsTextMessage(facade);
            jmsTextMessage.setText(textMsg);
            return jmsTextMessage;
        }).when(mockSession).createTextMessage(anyString());
    }

    @Test
    public void invalidSpecificOptionsThrowConnectionConfigurationInvalidException() {
        final Map<String, String> specificOptions = new HashMap<>();
        specificOptions.put("failover.unknown.option", "100");
        specificOptions.put("failover.nested.amqp.vhost", "ditto");
        final Connection connection = ConnectivityModelFactory.newConnectionBuilder(createRandomConnectionId(),
                        ConnectionType.AMQP_10, ConnectivityStatus.OPEN, TestConstants.getUriOfNewMockServer())
                .specificConfig(specificOptions)
                .sources(singletonList(
                        ConnectivityModelFactory.newSourceBuilder()
                                .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                                .address("source1")
                                .build()))
                .build();

        final ThrowableAssert.ThrowingCallable props1 =
                () -> AmqpClientActor.propsForTest(connection, ActorRef.noSender(), null, null, actorSystem);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> AmqpClientActor.propsForTest(connection, ActorRef.noSender(), null, jmsConnectionFactory,
                        actorSystem);

        Stream.of(props1, props2).forEach(throwingCallable ->
                assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                        .isThrownBy(throwingCallable)
                        .withMessageContaining("unknown.option"));
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(),
                            (c, e, l, i) -> {
                                throw JMS_EXCEPTION;
                            }, actorSystem);
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            final var failure = expectMsgClass(Status.Failure.class);

            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class)
                    .hasMessage("Failed to %s:%s", "create JMS connection", JMS_EXCEPTION.getMessage())
                    .hasCause(JMS_EXCEPTION);
            getActorSystem().stop(connectionActor);
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            aggregator.expectMsgClass(ResourceStatus.class);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            aggregator.expectMsgClass(ResourceStatus.class);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testReconnect() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // introduce artificial code difference from RabbitMQClientActorTest.testReconnect
            for (int i = 0; i < 10; ++i) {
                amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(DISCONNECTED_SUCCESS);

                amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);
            }

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testReconnectAndVerifyConnectionStatus() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            aggregator.expectMsgClass(ResourceStatus.class);

            final JmsConnectionListener connectionListener = checkNotNull(listenerArgumentCaptor.getValue());

            connectionListener.onConnectionInterrupted(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.MISCONFIGURED);

            connectionListener.onConnectionRestored(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.OPEN);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    private static void verifyConnectionStatus(final ActorRef amqpClientActor, final TestProbe aggregator,
            final ConnectivityStatus open) {

        amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
        Awaitility.await().until(() -> awaitStatusInStatusResponse(aggregator, open));
    }

    private static Boolean awaitStatusInStatusResponse(final TestProbe aggregator,
            final ConnectivityStatus expectedStatus) {
        return expectedStatus.equals(aggregator.expectMsgClass(ResourceStatus.class).getStatus());
    }

    @Test
    public void sendCommandDuringInit() {
        new TestKit(actorSystem) {{
            final CountDownLatch latch = new CountDownLatch(1);
            final Props props = AmqpClientActor.propsForTest(connection, getRef(), getRef(),
                    (c, e, l, i) -> waitForLatchAndReturn(latch, mockConnection), actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            latch.countDown();

            expectMsg(CONNECTED_SUCCESS);
        }};
    }

    @Test
    public void sendConnectCommandWhenAlreadyConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // no reconnect happens
            Mockito.verify(mockConnection, Mockito.times(1)).start();
        }};
    }

    @Test
    public void sendDisconnectWhenAlreadyDisconnected() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyNoInteractions(mockConnection);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            final var failure = expectMsgClass(Status.Failure.class);

            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class)
                    .hasMessage("Failed to %s:%s", "connect JMS client", JMS_EXCEPTION.getMessage())
                    .hasCause(JMS_EXCEPTION);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(anyInt())).thenThrow(JMS_EXCEPTION);
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            final var failure = expectMsgClass(Status.Failure.class);

            assertThat(failure.cause()).isInstanceOf(ConnectionFailedException.class)
                    .hasMessage("Failed to %s:%s", "create session", JMS_EXCEPTION.getMessage())
                    .hasCause(JMS_EXCEPTION);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            doReturn(mockSession).when(mockConnection).createSession(anyInt());
            when(mockSession.createConsumer(any())).thenThrow(JMS_EXCEPTION);
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testSetMessageListenerOnConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{

            // ProviderSecurityException resolves to MISCONFIGURED state
            final IllegalStateException jmsEx = new IllegalStateException("not allowed");
            jmsEx.initCause(new ProviderSecurityException("disallowed by local policy"));

            doThrow(jmsEx).when(mockConsumer).setMessageListener(any());
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);

            Awaitility.await()
                    .pollInterval(Duration.ofMillis(100))
                    .atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> {
                        amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()),
                                getRef());
                        fishForMessage(Duration.ofSeconds(1), "client status", o -> {
                            if (o instanceof ResourceStatus) {
                                final ResourceStatus resourceStatus = (ResourceStatus) o;
                                if (resourceStatus.getResourceType() == ResourceStatus.ResourceType.CLIENT) {
                                    assertThat((Object) resourceStatus.getStatus())
                                            .isEqualTo(ConnectivityStatus.MISCONFIGURED);
                                    return true;
                                }
                            }
                            return false;
                        });
                    });

            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testConnectionRestoredExpectRecreateSession() throws JMSException {

        final Target target = TestConstants.Targets.TWIN_TARGET;
        final JmsMessageConsumer recoveredConsumer = Mockito.mock(JmsMessageConsumer.class);
        final JmsSession newSession = Mockito.mock(JmsSession.class, withSettings().name("recoveredSession"));

        when(mockSession.isClosed()).thenReturn(true); // existing session was closed
        doReturn(mockSession) // initial session
                .doReturn(newSession) // recovered session
                .when(mockConnection)
                .createSession(anyInt());
        prepareSession(newSession, recoveredConsumer);

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            // connect
            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // capture connection listener to close session later
            final ArgumentCaptor<JmsConnectionListener> captor = ArgumentCaptor.forClass(JmsConnectionListener.class);
            verify(mockConnection).addConnectionListener(captor.capture());
            final JmsConnectionListener jmsConnectionListener = captor.getValue();

            // verify everything is setup correctly by publishing an event
            sendThingEventAndExpectPublish(amqpClientActor, target, () -> getProducerForAddress(target.getAddress()));
            // verify message is consumed and forwarded to concierge
            consumeMockMessage(mockConsumer);
            expectMsgClass(Command.class);

            // now close session
            jmsConnectionListener.onConnectionRestored(URI.create("amqp://broker:5671"));
            verify(mockConnection, timeout(2000).times(2)).createSession(anyInt());

            // close is called on old session
            verify(mockSession, timeout(2000).times(2)).close();

            // verify publishing an event works with new session/producer
            sendThingEventAndExpectPublish(amqpClientActor, target, () -> getProducerForAddress(target.getAddress()));

            // verify message is consumed with newly created consumer
            consumeMockMessage(recoveredConsumer);
            expectMsgClass(Command.class);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActor() throws JMSException {
        testConsumeMessageAndExpectForwardToProxyActor(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext())
                        .isEqualTo(Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    @Test
    public void testConsumeMessageForSourcesWithSameAddress() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID,
                        TestConstants.Sources.SOURCES_WITH_SAME_ADDRESS);

        final AtomicBoolean messageReceivedForGlobalContext = new AtomicBoolean(false);
        final AtomicBoolean messageReceivedForSourceContext = new AtomicBoolean(false);

        testConsumeMessageAndExpectForwardToProxyActor(connection, 2,
                c -> {
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForSourceContext.set(true);
                    }
                    if (c.getDittoHeaders()
                            .getAuthorizationContext()
                            .equals(Authorization.SOURCE_SPECIFIC_CONTEXT)) {
                        messageReceivedForGlobalContext.set(true);
                    }
                });

        assertThat(messageReceivedForGlobalContext.get() && messageReceivedForSourceContext.get()).isTrue();
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActorWithCorrectAuthContext() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID,
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        testConsumeMessageAndExpectForwardToProxyActor(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext())
                        .isEqualTo(Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    private void testConsumeMessageAndExpectForwardToProxyActor(final Connection connection,
            final int consumers, final Consumer<Command> commandConsumer) throws JMSException {
        testConsumeMessageAndExpectForwardToProxyActor(connection, consumers, commandConsumer, null);
    }

    private void testConsumeMessageAndExpectForwardToProxyActor(final Connection connection,
            final int consumers,
            final Consumer<Command> commandConsumer,
            @Nullable final Consumer<ActorRef> postStep) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeast(consumers)).setMessageListener(captor.capture());
            for (final MessageListener messageListener : captor.getAllValues()) {
                messageListener.onMessage(mockMessage());
            }

            for (int i = 0; i < consumers; i++) {
                final Command<?> command = expectMsgClass(Command.class);
                assertThat(command).isInstanceOf(SignalWithEntityId.class);
                assertThat((CharSequence) ((SignalWithEntityId<?>) command).getEntityId()).isEqualTo(
                        TestConstants.Things.THING_ID);
                assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
                commandConsumer.accept(command);
            }

            if (postStep != null) {
                postStep.accept(amqpClientActor);
            }
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponse() throws JMSException {
        testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponse(
                connection, (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replyTarget/",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponseForConnectionWithoutTarget()
            throws JMSException {

        final String targetsKey = Connection.JsonFields.TARGETS.getPointer().toString();
        final Connection connectionWithoutTargets
                = ConnectivityModelFactory.connectionFromJson(connection.toJson().remove(targetsKey));

        testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponse(
                connectionWithoutTargets,
                (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replyTarget/",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToProxyActorAndReceiveError() throws JMSException {
        testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponse(
                connection, (id, headers) -> ThingErrorResponse.of(id,
                        ThingNotModifiableException.newBuilder(id).dittoHeaders(headers).build()),
                "replyTarget/",
                message -> message.contains("ditto/thing/things/twin/errors"));
    }

    private void testConsumeMessageAndExpectForwardToProxyActorAndReceiveResponse(final Connection connection,
            final BiFunction<ThingId, DittoHeaders, CommandResponse> responseSupplier,
            final String expectedAddressPrefix,
            final Predicate<String> messageTextPredicate) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();
            final Message incomingMessage = mockMessage();
            messageListener.onMessage(incomingMessage);

            final ThingCommand<?> command = expectMsgClass(ThingCommand.class);
            assertThat((CharSequence) command.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
            assertThat(command).isInstanceOf(ModifyThing.class);

            getLastSender().tell(responseSupplier.apply(command.getEntityId(), command.getDittoHeaders()), getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            // verify that the message is published via the producer with the correct destination
            final MessageProducer messageProducer =
                    getProducerForAddress(expectedAddressPrefix + command.getEntityId());
            verify(messageProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message outgoingMessage = messageCaptor.getValue();
            assertThat(outgoingMessage).isNotNull();
            assertThat(messageTextPredicate).accepts(outgoingMessage.getBody(String.class));
        }};
    }

    @Test
    public void testTargetAddressPlaceholderReplacement() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID,
                        TestConstants.Targets.TARGET_WITH_PLACEHOLDER);

        // target Placeholder: target:{{ thing:namespace }}/{{thing:name}}@{{ topic:channel }}
        final String expectedAddress =
                "target:" + TestConstants.Things.NAMESPACE + "/" + TestConstants.Things.ID + "@" +
                        TopicPath.Channel.TWIN.getName();

        new TestKit(actorSystem) {{
            final Connection connectionWithTarget = connection.toBuilder()
                    .setTargets(List.of(
                            ConnectivityModelFactory.newTargetBuilder()
                                    .address(TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress())
                                    .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                                    .topics(Topic.TWIN_EVENTS)
                                    .build()
                    ))
                    .build();
            final Props props =
                    AmqpClientActor.propsForTest(connectionWithTarget, getRef(), getRef(),
                            (c, e, l, i) -> mockConnection, actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent<?> thingModifiedEvent =
                    TestConstants.thingModified(Authorization.AUTHORIZATION_CONTEXT.getAuthorizationSubjects());

            amqpClientActor.tell(thingModifiedEvent, getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            final MessageProducer messageProducer = getProducerForAddress(expectedAddress);
            verify(messageProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message message = messageCaptor.getValue();
            assertThat(message).isNotNull();
        }};
    }

    @Test
    public void testReceiveThingEventAndExpectForwardToJMSProducer() throws JMSException {
        final Target target = TestConstants.Targets.TWIN_TARGET;
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTest(connection, getRef(), getRef(),
                    (c, e, l, i) -> mockConnection, actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            sendThingEventAndExpectPublish(amqpClientActor, target, () -> getProducerForAddress(target.getAddress()));
        }};
    }

    @Test
    public void testConsumerRecreationFailureWhenConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(singleConsumerConnection(), getRef(), getRef(),
                            (c, e, l, i) -> mockConnection, actorSystem);
            final TestActorRef<AmqpClientActor> amqpClientActorRef = TestActorRef.apply(props, actorSystem);
            final AmqpClientActor amqpClientActor = amqpClientActorRef.underlyingActor();

            amqpClientActorRef.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // GIVEN: JMS session fails, but the JMS connection can create a new functional session
            final Session mockSession2 = Mockito.mock(Session.class);
            final MessageConsumer mockConsumer2 = Mockito.mock(JmsMessageConsumer.class);
            when(mockSession.createConsumer(any()))
                    .thenThrow(new IllegalStateException("expected exception"));
            doReturn(mockSession2).when(mockConnection).createSession(anyInt());
            doReturn(mockConsumer2).when(mockSession2).createConsumer(any());

            // WHEN: consumer is closed and cannot be recreated
            final ActorRef amqpConsumerActor = amqpClientActor.context().children().toStream()
                    .find(child -> child.path().name().startsWith(AmqpConsumerActor.ACTOR_NAME_PREFIX))
                    .get();
            final Throwable error = new IllegalStateException("Forcibly detached");
            final Status.Failure failure = new Status.Failure(new AskTimeoutException("Consumer creation timeout"));
            amqpClientActor.connectionListener.onConsumerClosed(mockConsumer, error);
            verify(mockSession, atLeastOnce()).createConsumer(any());
            amqpConsumerActor.tell(failure, amqpConsumerActor);

            // THEN: connection gets restarted
            verify(mockConnection).createSession(anyInt());
            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer2, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();

            // THEN: recreated connection is working
            messageListener.onMessage(mockMessage());
            expectMsgClass(Command.class);
            getActorSystem().stop(amqpClientActorRef);
        }};
    }

    @Test
    public void testSpecialCharactersInSourceAndRequestMetrics() throws JMSException {
        new TestKit(actorSystem) {
            {
                final String sourceWithSpecialCharacters =
                        IntStream.range(32, 255).mapToObj(i -> (char) i)
                                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                                .toString();
                final String sourceWithUnicodeCharacters =
                        "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04" +
                                "\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE0B\uD83D\uDE0C" +
                                "\uD83D\uDE0D\uD83D\uDE0E\uD83D\uDE0F";

                final Source source = ConnectivityModelFactory.newSourceBuilder()
                        .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                        .address(sourceWithSpecialCharacters)
                        .address(sourceWithUnicodeCharacters)
                        .build();

                final ConnectionId connectionId = createRandomConnectionId();
                final Connection connectionWithSpecialCharacters =
                        TestConstants.createConnection(connectionId, singletonList(source));

                testConsumeMessageAndExpectForwardToProxyActor(connectionWithSpecialCharacters, 1, cmd -> {
                    // nothing to do here
                }, ref -> {
                    ref.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getRef());

                    fishForMessage(duration("3s"), "fishing for OPEN status",
                            msg -> isExpectedMessage(msg, sourceWithSpecialCharacters, sourceWithUnicodeCharacters));
                    fishForMessage(duration("3s"), "fishing for OPEN status",
                            msg -> isExpectedMessage(msg, sourceWithSpecialCharacters, sourceWithUnicodeCharacters));
                });
            }

            private boolean isExpectedMessage(final Object o, final String... expectedSources) {
                if (!(o instanceof ResourceStatus)) {
                    return false;
                }
                final ResourceStatus resourceStatus = (ResourceStatus) o;
                return resourceStatus.getResourceType() == ResourceStatus.ResourceType.SOURCE
                        && Arrays.asList(expectedSources).contains(resourceStatus.getAddress().get())
                        && ConnectivityStatus.OPEN.equals(resourceStatus.getStatus());
            }
        };
    }

    @Test
    public void testRetrieveConnectionStatus() throws JMSException {
        new TestKit(actorSystem) {{
            final String sourceWithSpecialCharacters =
                    IntStream.range(32, 255).mapToObj(i -> (char) i)
                            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                            .toString();
            final String sourceWithUnicodeCharacters =
                    "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04" +
                            "\uD83D\uDE05\uD83D\uDE06\uD83D\uDE07\uD83D\uDE08\uD83D\uDE09\uD83D\uDE0A\uD83D\uDE0B\uD83D\uDE0C" +
                            "\uD83D\uDE0D\uD83D\uDE0E\uD83D\uDE0F";

            final Source source = ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(Authorization.AUTHORIZATION_CONTEXT)
                    .address(sourceWithSpecialCharacters)
                    .address(sourceWithUnicodeCharacters)
                    .build();

            final ConnectionId connectionId = createRandomConnectionId();
            final Connection connectionWithSpecialCharacters =
                    TestConstants.createConnection(connectionId, singletonList(source));

            testConsumeMessageAndExpectForwardToProxyActor(connectionWithSpecialCharacters, 1, cmd -> {
                // nothing to do here
            }, ref -> ref.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getRef()));
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                            actorSystem);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Success.class);
            getActorSystem().stop(amqpClientActor);
        }};
    }

    @Test
    public void testTestConnectionFailsOnTimeout() throws JMSException {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            new TestKit(actorSystem) {{
                when(mockConnection.createSession(anyInt()))
                        .thenAnswer(invocationOnMock -> waitForLatchAndReturn(latch, mockSession));
                final Props props =
                        AmqpClientActor.propsForTest(connection, getRef(), getRef(), (c, e, l, i) -> mockConnection,
                                actorSystem);
                final ActorRef amqpClientActor = actorSystem.actorOf(props);

                amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
                amqpClientActor.tell(FSM.StateTimeout$.MODULE$, amqpClientActor);
                expectMsgClass(Status.Failure.class);
                getActorSystem().stop(amqpClientActor);
            }};
        } finally {
            latch.countDown();
        }
    }

    private static Message mockMessage() throws JMSException {
        final AmqpJmsTextMessageFacade amqpJmsTextMessageFacade = new AmqpJmsTextMessageFacade();
        amqpJmsTextMessageFacade.setContentType(Symbol.getSymbol(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE));
        amqpJmsTextMessageFacade.initialize(Mockito.mock(AmqpConnection.class));

        final TextMessage jmsTextMessage = new JmsTextMessage(amqpJmsTextMessageFacade);
        jmsTextMessage.setJMSCorrelationID("cid");
        jmsTextMessage.setJMSReplyTo(new JmsQueue("reply"));
        jmsTextMessage.setText(TestConstants.modifyThing());
        return jmsTextMessage;
    }

    private static <T> T waitForLatchAndReturn(final CountDownLatch latch, final T result) {
        try {
            latch.await();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    protected Connection getConnection(final boolean isSecure) {
        return isSecure ? setScheme(connection, "amqps") : connection;
    }

    @Override
    protected Props createClientActor(final ActorRef proxyActor, final Connection connection) {
        return AmqpClientActor.propsForTest(connection, proxyActor, connectionActor, (c, e, l, i) -> mockConnection,
                actorSystem);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    private static void consumeMockMessage(final MessageConsumer mockConsumer) throws JMSException {
        final ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        verify(mockConsumer, timeout(1000).atLeast(1)).setMessageListener(listener.capture());
        listener.getValue().onMessage(mockMessage());
    }

    private static void sendThingEventAndExpectPublish(final ActorRef amqpClientActor,
            final Target target,
            final Supplier<MessageProducer> messageProducerSupplier)
            throws JMSException {

        final String uuid = UUID.randomUUID().toString();
        final ThingModifiedEvent<?> thingModifiedEvent =
                TestConstants.thingModified(List.of(), Attributes.newBuilder().set("uuid", uuid).build())
                        .setDittoHeaders(DittoHeaders.newBuilder()
                                .putHeader("reply-to", target.getAddress())
                                .readGrantedSubjects(target.getAuthorizationContext().getAuthorizationSubjects())
                                .build());

        amqpClientActor.tell(thingModifiedEvent, ActorRef.noSender());

        final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
        final MessageProducer messageProducer = messageProducerSupplier.get();
        verify(messageProducer, timeout(2000).times(1))
                .send(messageCaptor.capture(), any(CompletionListener.class));

        final Message message = messageCaptor.getValue();
        assertThat(message).isNotNull();
        assertThat(message.getBody(String.class)).contains(uuid);
        assertThat(message.getBody(String.class)).contains(
                TestConstants.Things.NAMESPACE + "/" + TestConstants.Things.ID + "/" +
                        TopicPath.Group.THINGS.getName() + "/" + TopicPath.Channel.TWIN.getName() + "/" +
                        TopicPath.Criterion.EVENTS.getName() + "/" + TopicPath.Action.MODIFIED.getName());
    }

    /**
     * @return the producer for the given address created in the current session
     */
    private MessageProducer getProducerForAddress(final String address) {
        // it may take some time until the producers have been created
        return Awaitility.await()
                .atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> mockProducers.stream()
                        .filter(p -> address.equals(wrapThrowable(p::getDestination).toString()))
                        // we only want the latest producer (required to test session recovery)
                        .reduce((first, second) -> second)
                        .orElse(null), Matchers.notNullValue());
    }

    private Connection singleConsumerConnection() {
        final Source defaultSource = connection.getSources().get(0);
        return connection.toBuilder()
                .clientCount(1)
                .setSources(Collections.singletonList(ConnectivityModelFactory.newSourceBuilder()
                        .address(defaultSource.getAddresses().iterator().next())
                        .authorizationContext(defaultSource.getAuthorizationContext())
                        .consumerCount(1)
                        .enforcement(defaultSource.getEnforcement().orElse(null))
                        .headerMapping(defaultSource.getHeaderMapping())
                        .index(0)
                        .build()))
                .build();
    }

    /**
     * Wraps {@link Throwable} in {@link RuntimeException}.
     */
    private static <T> T wrapThrowable(final Retry.ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
