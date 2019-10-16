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
package org.eclipse.ditto.services.connectivity.messaging.amqp;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.connectivity.messaging.TestConstants.createRandomConnectionId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.URI;
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
import org.apache.qpid.proton.amqp.Symbol;
import org.assertj.core.api.ThrowableAssert;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.eclipse.ditto.model.base.common.DittoConstants;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.ResourceStatus;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.Topic;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.services.connectivity.messaging.AbstractBaseClientActorTest;
import org.eclipse.ditto.services.connectivity.messaging.BaseClientState;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants;
import org.eclipse.ditto.services.connectivity.messaging.TestConstants.Authorization;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.models.connectivity.OutboundSignalFactory;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.connectivity.exceptions.ConnectionFailedException;
import org.eclipse.ditto.signals.commands.connectivity.modify.CloseConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
import org.eclipse.ditto.signals.commands.connectivity.modify.TestConnection;
import org.eclipse.ditto.signals.commands.connectivity.query.RetrieveConnectionStatus;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotModifiableException;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
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
    private static final ConnectionFailedException SESSION_EXCEPTION =
            ConnectionFailedException.newBuilder(CONNECTION_ID).build();

    private static ActorSystem actorSystem;
    private static Connection connection;
    private final ConnectivityStatus connectionStatus = ConnectivityStatus.OPEN;

    @Mock
    private final JmsConnection mockConnection = Mockito.mock(JmsConnection.class);
    private final JmsConnectionFactory jmsConnectionFactory = (connection1, exceptionListener) -> mockConnection;
    @Mock
    private final JmsSession mockSession = Mockito.mock(JmsSession.class);
    @Mock
    private final JmsMessageConsumer mockConsumer = Mockito.mock(JmsMessageConsumer.class);

    private final List<MessageProducer> mockProducers = new LinkedList<>();

    private ArgumentCaptor<JmsConnectionListener> listenerArgumentCaptor;

    @BeforeClass
    public static void setUp() {
        actorSystem = ActorSystem.create("AkkaTestSystem", TestConstants.CONFIG);
        connection = TestConstants.createConnection(CONNECTION_ID);
    }

    @AfterClass
    public static void tearDown() {
        TestKit.shutdownActorSystem(actorSystem, scala.concurrent.duration.Duration.apply(5, TimeUnit.SECONDS),
                false);
    }

    @AfterClass
    public static void stopMockServers() {
        TestConstants.stopMockServers();
    }

    @Before
    public void init() throws JMSException {
        Mockito.reset(mockConnection, mockSession, mockConsumer);
        when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
        listenerArgumentCaptor = ArgumentCaptor.forClass(JmsConnectionListener.class);
        doNothing().when(mockConnection).addConnectionListener(listenerArgumentCaptor.capture());
        prepareSession(mockSession, mockConsumer);
    }

    private void prepareSession(final Session mockSession, final JmsMessageConsumer mockConsumer) throws JMSException {
        when(mockSession.createConsumer(any(JmsQueue.class))).thenReturn(mockConsumer);
        when(mockSession.createProducer(any(Destination.class))).thenAnswer(
                (Answer<MessageProducer>) destinationInv -> {
                    final MessageProducer messageProducer = mock(MessageProducer.class);
                    when(messageProducer.getDestination()).thenReturn(destinationInv.getArgument(0));
                    mockProducers.add(messageProducer);
                    return messageProducer;
                });
        when(mockSession.createTextMessage(anyString())).thenAnswer((Answer<JmsMessage>) textMsgInv -> {
            final String textMsg = textMsgInv.getArgument(0);
            final AmqpJmsTextMessageFacade facade = new AmqpJmsTextMessageFacade();
            facade.initialize(Mockito.mock(AmqpConnection.class));
            final JmsTextMessage jmsTextMessage = new JmsTextMessage(facade);
            jmsTextMessage.setText(textMsg);
            return jmsTextMessage;
        });
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
                () -> AmqpClientActor.propsForTests(connection, null, null);
        final ThrowableAssert.ThrowingCallable props2 =
                () -> AmqpClientActor.propsForTests(connection, null, jmsConnectionFactory);

        Stream.of(props1, props2).forEach(throwingCallable ->
                assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                        .isThrownBy(throwingCallable)
                        .withMessageContaining("unknown.option"));
    }

    @Test
    public void testExceptionDuringJMSConnectionCreation() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (theConnection, exceptionListener) -> {
                                throw JMS_EXCEPTION;
                            });
            final ActorRef connectionActor = actorSystem.actorOf(props);

            connectionActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());

            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testConnectionHandling() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (connection1, exceptionListener) -> mockConnection);
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
        }};
    }

    @Test
    public void testReconnect() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (connection1, exceptionListener) -> mockConnection);
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
        }};
    }

    @Test
    public void testReconnectAndVerifyConnectionStatus() {
        new TestKit(actorSystem) {{
            final TestProbe aggregator = new TestProbe(actorSystem);

            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (connection1, exceptionListener) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);
            watch(amqpClientActor);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(RetrieveConnectionStatus.of(CONNECTION_ID, DittoHeaders.empty()), aggregator.ref());
            aggregator.expectMsgClass(ResourceStatus.class);

            final JmsConnectionListener connectionListener = checkNotNull(listenerArgumentCaptor.getValue());

            connectionListener.onConnectionInterrupted(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.FAILED);

            connectionListener.onConnectionRestored(DUMMY);
            verifyConnectionStatus(amqpClientActor, aggregator, ConnectivityStatus.OPEN);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
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
            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> waitForLatchAndReturn(latch, mockConnection));
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
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (ac, el) -> mockConnection);
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
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
            Mockito.verifyZeroInteractions(mockConnection);
        }};
    }

    @Test
    public void testStartConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).start();
            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateSessionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).createSession(Session.CLIENT_ACKNOWLEDGE);
            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(new Status.Failure(SESSION_EXCEPTION));
        }};
    }

    @Test
    public void testCreateConsumerFails() throws JMSException {
        new TestKit(actorSystem) {{
            when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE)).thenReturn(mockSession);
            doThrow(JMS_EXCEPTION).when(mockSession).createConsumer(any());
            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Failure.class);
        }};
    }

    @Test
    public void testCloseConnectionFails() throws JMSException {
        new TestKit(actorSystem) {{
            doThrow(JMS_EXCEPTION).when(mockConnection).close();
            final Props props =
                    AmqpClientActor.propsForTests(connection,
                            getRef(), (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            amqpClientActor.tell(CloseConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(DISCONNECTED_SUCCESS);
        }};
    }

    @Test
    public void testConnectionRestoredExpectRecreateSession() throws JMSException {

        final Target target = TestConstants.Targets.TWIN_TARGET;
        final JmsMessageConsumer recoveredConsumer = Mockito.mock(JmsMessageConsumer.class);
        final JmsSession newSession = Mockito.mock(JmsSession.class, withSettings().name("recoveredSession"));

        when(mockSession.isClosed()).thenReturn(true); // existing session was closed
        when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE))
                .thenReturn(mockSession) // initial session
                .thenReturn(newSession); // recovered session
        prepareSession(newSession, recoveredConsumer);

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(), (ac, el) -> mockConnection);
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
            verify(mockConnection, timeout(2000).times(2)).createSession(Session.CLIENT_ACKNOWLEDGE);

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
    public void testConsumeMessageAndExpectForwardToConciergeForwarder() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    @Test
    public void testConsumeMessageForSourcesWithSameAddress() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID,
                        TestConstants.Sources.SOURCES_WITH_SAME_ADDRESS);

        final AtomicBoolean messageReceivedForGlobalContext = new AtomicBoolean(false);
        final AtomicBoolean messageReceivedForSourceContext = new AtomicBoolean(false);

        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 2,
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
    public void testConsumeMessageAndExpectForwardToConciergeForwarderWithCorrectAuthContext() throws JMSException {
        final Connection connection =
                TestConstants.createConnection(CONNECTION_ID,
                        TestConstants.Sources.SOURCES_WITH_AUTH_CONTEXT);
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, 1,
                c -> assertThat(c.getDittoHeaders().getAuthorizationContext()).isEqualTo(
                        Authorization.SOURCE_SPECIFIC_CONTEXT));
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers, final Consumer<Command> commandConsumer) throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarder(connection, consumers, commandConsumer, null);
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarder(final Connection connection,
            final int consumers,
            final Consumer<Command> commandConsumer,
            @Nullable final Consumer<ActorRef> postStep) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeast(consumers)).setMessageListener(captor.capture());
            for (final MessageListener messageListener : captor.getAllValues()) {
                messageListener.onMessage(mockMessage());
            }

            for (int i = 0; i < consumers; i++) {
                final Command command = expectMsgClass(Command.class);
                assertThat((CharSequence) command.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
                assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
                commandConsumer.accept(command);
            }

            if (postStep != null) {
                postStep.accept(amqpClientActor);
            }
        }};
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                connection, (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replies",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponseForConnectionWithoutTarget()
            throws JMSException {

        final String targetsKey = Connection.JsonFields.TARGETS.getPointer().toString();
        final Connection connectionWithoutTargets
                = ConnectivityModelFactory.connectionFromJson(connection.toJson().remove(targetsKey));

        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                connectionWithoutTargets,
                (id, headers) -> ModifyThingResponse.modified(id, DittoHeaders.of(headers)),
                "replies",
                message -> message.contains("\"status\":2"));
    }

    @Test
    public void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveError() throws JMSException {
        testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
                connection, (id, headers) -> ThingErrorResponse.of(id,
                        ThingNotModifiableException.newBuilder(id).dittoHeaders(headers).build()),
                "replies",
                message -> message.contains("ditto/thing/things/twin/errors"));
    }

    private void testConsumeMessageAndExpectForwardToConciergeForwarderAndReceiveResponse(
            final Connection connection,
            final BiFunction<ThingId, DittoHeaders, CommandResponse> responseSupplier,
            final String expectedAddress,
            final Predicate<String> messageTextPredicate) throws JMSException {

        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();
            messageListener.onMessage(mockMessage());

            final ThingCommand command = expectMsgClass(ThingCommand.class);
            assertThat((CharSequence) command.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(command.getDittoHeaders().getCorrelationId()).contains(TestConstants.CORRELATION_ID);
            assertThat(command).isInstanceOf(ModifyThing.class);

            getLastSender().tell(responseSupplier.apply(command.getEntityId(), command.getDittoHeaders()), getRef());

            final ArgumentCaptor<JmsMessage> messageCaptor = ArgumentCaptor.forClass(JmsMessage.class);
            // verify that the message is published via the producer with the correct destination
            final MessageProducer messageProducer = getProducerForAddress(expectedAddress);
            verify(messageProducer, timeout(2000)).send(messageCaptor.capture(), any(CompletionListener.class));

            final Message message = messageCaptor.getValue();
            assertThat(message).isNotNull();
            assertThat(messageTextPredicate).accepts(message.getBody(String.class));
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
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            final ThingModifiedEvent thingModifiedEvent = TestConstants.thingModified(singletonList(""));

            final OutboundSignal outboundSignal = OutboundSignalFactory.newOutboundSignal(thingModifiedEvent,
                    singletonList(ConnectivityModelFactory.newTarget(
                            TestConstants.Targets.TARGET_WITH_PLACEHOLDER.getAddress(),
                            Authorization.AUTHORIZATION_CONTEXT,
                            null, null, Topic.TWIN_EVENTS))
            );

            amqpClientActor.tell(outboundSignal, getRef());

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
            final Props props = AmqpClientActor.propsForTests(connection, getRef(),
                    (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            sendThingEventAndExpectPublish(amqpClientActor, target, () -> getProducerForAddress(target.getAddress()));
        }};
    }

    @Test
    public void testConsumerClosedWhenConnected() throws JMSException {
        new TestKit(actorSystem) {{
            final Props props = AmqpClientActor.propsForTests(singleConsumerConnection(), getRef(),
                    (ac, el) -> mockConnection);
            final TestActorRef<AmqpClientActor> amqpClientActorRef = TestActorRef.apply(props, actorSystem);
            final AmqpClientActor amqpClientActor = amqpClientActorRef.underlyingActor();

            amqpClientActorRef.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
            expectMsg(CONNECTED_SUCCESS);

            // GIVEN: JMS session can create another consumer
            final MessageConsumer mockConsumer2 = Mockito.mock(JmsMessageConsumer.class);
            when(mockSession.createConsumer(any())).thenReturn(mockConsumer2);

            // WHEN: consumer closed by remote end
            final Throwable error = new IllegalStateException("Forcibly detached");
            amqpClientActor.connectionListener.onConsumerClosed(mockConsumer, error);

            // THEN: another consumer is created
            verify(mockSession, atLeastOnce()).createConsumer(any());
            final ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(mockConsumer2, timeout(1000).atLeastOnce()).setMessageListener(captor.capture());
            final MessageListener messageListener = captor.getValue();

            // THEN: the recreated consumer is working
            messageListener.onMessage(mockMessage());
            expectMsgClass(Command.class);
        }};
    }

    @Test
    public void testConsumerRecreationFailureWhenConnected() throws JMSException {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            new TestKit(actorSystem) {{
                final Props props =
                        AmqpClientActor.propsForTests(singleConsumerConnection(), getRef(),
                                (ac, el) -> mockConnection);
                final TestActorRef<AmqpClientActor> amqpClientActorRef = TestActorRef.apply(props, actorSystem);
                final AmqpClientActor amqpClientActor = amqpClientActorRef.underlyingActor();

                amqpClientActorRef.tell(OpenConnection.of(CONNECTION_ID, DittoHeaders.empty()), getRef());
                expectMsg(CONNECTED_SUCCESS);

                // GIVEN: JMS session fails, but the JMS connection can create a new functional session
                final Session mockSession2 = Mockito.mock(Session.class);
                final MessageConsumer mockConsumer2 = Mockito.mock(JmsMessageConsumer.class);
                when(mockSession.createConsumer(any())).thenAnswer(invocation ->
                        waitForLatchAndReturn(latch, mockConsumer));
                when(mockConnection.createSession(anyInt())).thenReturn(mockSession2);
                when(mockSession2.createConsumer(any())).thenReturn(mockConsumer2);

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
            }};
        } finally {
            latch.countDown();
        }
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

                testConsumeMessageAndExpectForwardToConciergeForwarder(connectionWithSpecialCharacters, 1, cmd -> {
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

            testConsumeMessageAndExpectForwardToConciergeForwarder(connectionWithSpecialCharacters, 1, cmd -> {
                // nothing to do here
            }, ref -> ref.tell(RetrieveConnectionStatus.of(connectionId, DittoHeaders.empty()), getRef()));
        }};
    }

    @Test
    public void testTestConnection() {
        new TestKit(actorSystem) {{
            final Props props =
                    AmqpClientActor.propsForTests(connection, getRef(),
                            (ac, el) -> mockConnection);
            final ActorRef amqpClientActor = actorSystem.actorOf(props);

            amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
            expectMsgClass(Status.Success.class);
        }};
    }

    @Test
    public void testTestConnectionFailsOnTimeout() throws JMSException {
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            new TestKit(actorSystem) {{
                when(mockConnection.createSession(Session.CLIENT_ACKNOWLEDGE))
                        .thenAnswer(invocationOnMock -> waitForLatchAndReturn(latch, mockSession));
                final Props props =
                        AmqpClientActor.propsForTests(connection, getRef(),
                                (ac, el) -> mockConnection);
                final ActorRef amqpClientActor = actorSystem.actorOf(props);

                amqpClientActor.tell(TestConnection.of(connection, DittoHeaders.empty()), getRef());
                amqpClientActor.tell(FSM.StateTimeout$.MODULE$, amqpClientActor);
                expectMsgClass(Status.Failure.class);
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
    protected Connection getConnection() {
        return connection;
    }

    @Override
    protected Props createClientActor(final ActorRef conciergeForwarder) {
        return AmqpClientActor.propsForTests(connection, conciergeForwarder, (ac, el) -> mockConnection);
    }

    @Override
    protected ActorSystem getActorSystem() {
        return actorSystem;
    }

    private void consumeMockMessage(final MessageConsumer mockConsumer) throws JMSException {
        final ArgumentCaptor<MessageListener> listener = ArgumentCaptor.forClass(MessageListener.class);
        verify(mockConsumer, timeout(1000).atLeast(1)).setMessageListener(listener.capture());
        listener.getValue().onMessage(mockMessage());
    }

    private void sendThingEventAndExpectPublish(final ActorRef amqpClientActor, final Target target,
            final Supplier<MessageProducer> messageProducerSupplier)
            throws JMSException {
        final String uuid = UUID.randomUUID().toString();
        final ThingModifiedEvent thingModifiedEvent =
                TestConstants.thingModified(singletonList(""), Attributes.newBuilder().set("uuid", uuid).build())
                        .setDittoHeaders(DittoHeaders.newBuilder().putHeader("reply-to", target.getAddress()).build());
        final OutboundSignal outboundSignal =
                OutboundSignalFactory.newOutboundSignal(thingModifiedEvent, singletonList(target));
        amqpClientActor.tell(outboundSignal, ActorRef.noSender());

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
                .atMost(Duration.TWO_SECONDS)
                .pollInterval(Duration.TWO_HUNDRED_MILLISECONDS)
                .until(() -> mockProducers.stream()
                        .filter(p -> address.equals(wrapThrowable(() -> p.getDestination()).toString()))
                        // we only want the latest producer (required to test session recovery)
                        .reduce((first, second) -> second)
                        .orElse(null), Matchers.notNullValue());
    }

    /**
     * Wraps {@link Throwable} in {@link RuntimeException}.
     */
    private <T> T wrapThrowable(Retry.ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Connection singleConsumerConnection() {
        final Source defaultSource = connection.getSources().get(0);
        return connection.toBuilder()
                .clientCount(1)
                .setSources(Collections.singletonList(
                        ConnectivityModelFactory.newSourceBuilder()
                                .address(defaultSource.getAddresses().iterator().next())
                                .authorizationContext(defaultSource.getAuthorizationContext())
                                .consumerCount(1)
                                .enforcement(defaultSource.getEnforcement().orElse(null))
                                .headerMapping(defaultSource.getHeaderMapping().orElse(null))
                                .index(0)
                                .build()
                ))
                .build();
    }
}
